package matchmaking;

import matchmaking.common.CsvStore;
import matchmaking.facade.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Scanner;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    @TempDir Path disk;
    private String operate(String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        App.run(new CsvStore(disk), new Scanner(input), new PrintStream(output, true, StandardCharsets.UTF_8));
        return output.toString(StandardCharsets.UTF_8);
    }
    @Test void examinerCanTriggerBothFailuresAndInspectTheirState() {
        String output = operate("1\n1000\n1100\n150\nn\n0\n1\n1000\n1100\n50\ny\n0\n2\n0\n");
        assertTrue(output.contains("Rollback order: A"));
        assertTrue(output.contains("Rollback order: B>A"));
        assertTrue(output.contains("C Session: REJECTED"));
        assertEquals(2, new ChainRepository(new CsvStore(disk)).all().size());
    }
    @Test void examinerCanResumeAndManuallyCompensate() {
        CsvStore store = new CsvStore(disk);
        MatchChain paused = new MatchmakingFacade(store).startMatch(1000, 1100, 50, false, 1);
        assertTrue(operate("3\n" + paused.id() + "\n0\n").contains("COMPLETED"));
        MatchChain another = new MatchmakingFacade(store).startMatch(1000, 1100, 50, false, 2);
        assertTrue(operate("4\n" + another.id() + "\n0\n").contains("B>A"));
    }
    @Test void malformedInputDoesNotKillTheMenu() {
        String output = operate("unknown\n1\nnot-a-number\n2\n3\nmissing\n0\n");
        assertTrue(output.contains("Unknown option"));
        assertTrue(output.contains("Enter a whole number"));
        assertTrue(output.contains("Unknown match ID"));
        assertTrue(output.contains("No matches saved"));
    }
    @Test void mainRunsAgainstTheSuppliedStorageDirectory() {
        InputStream originalInput = System.in;
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream("1\n1000\n1100\n50\nn\n0\n2\n0\n".getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            App.main(new String[]{disk.toString()});
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
        }
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("C Session: CONFIRMED"));
        assertEquals(MatchChain.Stage.COMPLETED, new ChainRepository(new CsvStore(disk)).all().get(0).stage());
    }

    @Test void unexpectedEndOfInputExitsWithoutAnIncompleteMatch() {
        assertDoesNotThrow(() -> operate("1\n1000\n"));
        assertTrue(new ChainRepository(new CsvStore(disk)).all().isEmpty());
    }

    @Test void invalidCheatFlagIsRejectedInsteadOfSilentlyPassing() {
        assertTrue(operate("1\n1000\n1100\n50\nmaybe\n0\n").contains("Enter y or n"));
        assertTrue(new ChainRepository(new CsvStore(disk)).all().isEmpty());
    }
}
