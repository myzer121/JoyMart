import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

/**
 * ASM-based class relocator. Equivalent to maven-shade-plugin relocations.
 * Rewrites both the class's own internal name AND all references, and
 * correctly updates UTF-8 constant-pool lengths (unlike naive binary replace).
 *
 * Usage: RelocateTool <srcDir> <dstDir>
 */
public class RelocateTool {

    static final Map<String, String> RULES = Map.of(
            "org/bstats",            "me/nikl/gamebox/common/bstats",
            "com/zaxxer/hikari",     "me/nikl/gamebox/common/hikari",
            "net/jodah/expiringmap", "me/nikl/gamebox/common/expiringmap",
            "org/slf4j",             "me/nikl/gamebox/common/slf4j",
            "javax/annotation",      "me/nikl/gamebox/common/jsr305"
    );

    static Remapper remapper = new Remapper() {
        @Override
        public String map(String internalName) {
            for (Map.Entry<String, String> e : RULES.entrySet()) {
                if (internalName.equals(e.getKey())) return e.getValue();
                String prefix = e.getKey() + "/";
                if (internalName.startsWith(prefix)) {
                    return e.getValue() + "/" + internalName.substring(prefix.length());
                }
            }
            return internalName;
        }
    };

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: RelocateTool <srcDir> <dstDir>");
            System.exit(1);
        }
        Path src = Paths.get(args[0]);
        Path dst = Paths.get(args[1]);
        if (Files.exists(dst)) {
            Files.walkFileTree(dst, new SimpleFileVisitor<>() {
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                    Files.delete(f); return FileVisitResult.CONTINUE;
                }
                public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                    Files.delete(d); return FileVisitResult.CONTINUE;
                }
            });
        }
        Files.createDirectories(dst);

        final int[] count = {0};
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = src.relativize(file).toString().replace('\\', '/');
                // Compute destination path by applying rules to the path
                String newRel = rel;
                for (Map.Entry<String, String> e : RULES.entrySet()) {
                    String oldDisk = e.getKey().replace('/', '/');
                    String newDisk = e.getValue().replace('/', '/');
                    newRel = newRel.replace(oldDisk, newDisk);
                }
                Path out = dst.resolve(newRel.replace('/', FileSystems.getDefault().getSeparator().charAt(0)));
                Files.createDirectories(out.getParent());

                if (rel.endsWith(".class") && !rel.endsWith("module-info.class")) {
                    byte[] data = Files.readAllBytes(file);
                    try {
                        ClassReader cr = new ClassReader(data);
                        ClassWriter cw = new ClassWriter(0);
                        ClassRemapper remapperVisitor = new ClassRemapper(cw, RelocateTool.remapper);
                        cr.accept(remapperVisitor, 0);
                        Files.write(out, cw.toByteArray());
                    } catch (Exception e) {
                        // If ASM fails, skip this class but warn
                        System.err.println("WARN: could not relocate " + rel + ": " + e.getMessage());
                        Files.write(out, data);
                    }
                    count[0]++;
                } else {
                    Files.copy(file, out, StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        System.out.println("Relocated " + count[0] + " class files from " + src + " -> " + dst);
    }
}
