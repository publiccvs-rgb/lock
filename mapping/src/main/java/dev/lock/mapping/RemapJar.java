package dev.lock.mapping;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Build-time tool: produces a compile-only copy of the Minecraft client jar with Mojang names.
 * The output is a local development artifact and is never distributed.
 */
public final class RemapJar {
    public static void main(String[] args) throws IOException {
        if (args.length < 3 || args[0].isEmpty()) {
            System.err.println("usage: RemapJar <client.jar> <client.txt> <out.jar>");
            System.exit(2);
        }
        remap(Paths.get(args[0]), Paths.get(args[1]), Paths.get(args[2]));
    }

    public static void remap(Path in, Path mappingsFile, Path out) throws IOException {
        Mappings m;
        try (var r = Files.newBufferedReader(mappingsFile, StandardCharsets.UTF_8)) {
            m = Mappings.parse(r);
        }
        try (ZipFile zip = new ZipFile(in.toFile())) {
            ClassHierarchy h = ClassHierarchy.fromBytes(name -> {
                ZipEntry e = zip.getEntry(name + ".class");
                if (e == null) return null;
                try (InputStream is = zip.getInputStream(e)) {
                    return is.readAllBytes();
                } catch (IOException ex) {
                    return null;
                }
            });
            HierarchyRemapper remapper = new HierarchyRemapper(m.toNamed, h);
            Files.createDirectories(out.toAbsolutePath().getParent());
            Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
            int count = 0;
            try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream zos = new ZipOutputStream(os)) {
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (!e.getName().endsWith(".class")) continue;
                    byte[] b;
                    try (InputStream is = zip.getInputStream(e)) {
                        b = is.readAllBytes();
                    }
                    ClassReader cr = new ClassReader(b);
                    ClassWriter cw = new ClassWriter(0);
                    cr.accept(new ClassRemapper(cw, remapper), 0);
                    String name = remapper.map(cr.getClassName());
                    zos.putNextEntry(new ZipEntry(name + ".class"));
                    zos.write(cw.toByteArray());
                    zos.closeEntry();
                    count++;
                }
            }
            Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Remapped " + count + " classes to " + out);
        }
    }
}
