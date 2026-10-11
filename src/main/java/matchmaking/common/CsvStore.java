package matchmaking.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

// One CSV record per file. No open handles or in-memory database.
public class CsvStore {
    private final Path root;

    public CsvStore(Path root) { this.root = root; }

    private Path file(String table, String id) {
        if (!id.matches("[A-Za-z0-9-]+")) {
            throw new MatchmakingException("Invalid match ID");
        }
        return root.resolve(table).resolve(id + ".csv");
    }

    public String[] read(String table, String id) {
        Path path = file(table, id);
        if (!Files.exists(path)) return null;
        try {
            return Files.readString(path, StandardCharsets.UTF_8).strip().split(",", -1);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public void write(String table, String id, String... fields) {
        Path path = file(table, id);
        Path temporary = path.resolveSibling(id + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            byte[] bytes = (String.join(",", fields) + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileChannel out = FileChannel.open(temporary, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) out.write(buffer);
                out.force(true);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public List<String> ids(String table) {
        Path folder = root.resolve(table);
        if (!Files.exists(folder)) return List.of();
        try (var paths = Files.list(folder)) {
            return paths.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".csv"))
                    .map(n -> n.substring(0, n.length() - 4)).sorted().toList();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
