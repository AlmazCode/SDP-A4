package matchmaking;

import matchmaking.common.*;
import matchmaking.facade.*;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class App {
    public static void main(String[] args) {
        Path data = Path.of(args.length == 0 ? "data" : args[0]);
        try (Scanner input = new Scanner(System.in)) {
            run(new CsvStore(data), input, System.out);
        }
    }

    public static void run(CsvStore store, Scanner input, PrintStream out) {
        MatchmakingFacade matchmaking = new MatchmakingFacade(store);
        AdminFacade admin = new AdminFacade(store);
        out.println("Video Game Matchmaking");
        while (true) {
            out.println("1 New match | 2 View records | 3 Resume match | 4 Cancel match | 0 Exit");
            if (!input.hasNextLine()) return;
            String option = input.nextLine().strip();
            try {
                switch (option) {
                    case "0" -> { return; }
                    case "1" -> {
                        int first = integer(input, out, "Player 1 rating (0-5000): ");
                        int second = integer(input, out, "Player 2 rating (0-5000): ");
                        int latency = integer(input, out, "Server latency (ms): ");
                        out.print("Cheat detected? y/n: ");
                        boolean cheater = switch (input.nextLine().strip().toLowerCase()) {
                            case "y" -> true;
                            case "n" -> false;
                            default -> throw new MatchmakingException("Enter y or n");
                        };
                        int pause = integer(input, out, "Pause: 0 full run, 1 after A, 2 after B: ");
                        show(matchmaking.startMatch(first, second, latency, cheater, pause), out);
                    }
                    case "2" -> out.print(admin.inspect());
                    case "3", "4" -> {
                        out.print("Match ID: ");
                        String id = input.nextLine().strip();
                        show(option.equals("3") ? matchmaking.resume(id) : admin.compensate(id), out);
                    }
                    default -> out.println("Unknown option");
                }
            } catch (NumberFormatException e) {
                out.println("Enter a whole number.");
            } catch (MatchmakingException e) {
                out.println("Error: " + e.getMessage());
            } catch (UncheckedIOException e) {
                out.println("Disk error: " + e.getCause().getMessage() + ". Fix the disk problem, then resume the match.");
            } catch (NoSuchElementException e) {
                return;
            }
        }
    }

    private static int integer(Scanner input, PrintStream out, String prompt) {
        out.print(prompt);
        return Integer.parseInt(input.nextLine().strip());
    }

    private static void show(MatchChain match, PrintStream out) {
        out.println("ID: " + match.id() + " | Chain: " + match.stage());
        if (!match.message().isEmpty()) out.println("Reason: " + match.message());
        if (!match.rollback().isEmpty()) out.println("Rollback order: " + match.rollback());
    }
}
