import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Library Management System (console based, single file).
 *
 * Features:
 *  - Add / remove / search / list books
 *  - Register / list members
 *  - Issue and return books (14-day loan period, fine per overdue day)
 *  - View a member's borrowed books and the full transaction history
 *
 * Compile:  javac LibraryManagementSystem.java
 * Run:      java LibraryManagementSystem
 */
public class LibraryManagementSystem {

    // ===================== MODEL CLASSES =====================

    static class Book {
        private final String isbn;
        private String title;
        private String author;
        private String category;
        private int totalCopies;
        private int availableCopies;

        Book(String isbn, String title, String author, String category, int copies) {
            this.isbn = isbn;
            this.title = title;
            this.author = author;
            this.category = category;
            this.totalCopies = copies;
            this.availableCopies = copies;
        }

        String getIsbn() { return isbn; }
        String getTitle() { return title; }
        String getAuthor() { return author; }
        String getCategory() { return category; }
        int getTotalCopies() { return totalCopies; }
        int getAvailableCopies() { return availableCopies; }

        boolean isAvailable() { return availableCopies > 0; }

        void addCopies(int n) {
            totalCopies += n;
            availableCopies += n;
        }

        void checkOut() {
            if (availableCopies <= 0) throw new IllegalStateException("No copies available.");
            availableCopies--;
        }

        void checkIn() {
            if (availableCopies < totalCopies) availableCopies++;
        }

        int borrowedCopies() { return totalCopies - availableCopies; }

        @Override
        public String toString() {
            return String.format("%-14s | %-30s | %-20s | %-12s | %d/%d available",
                    isbn, truncate(title, 30), truncate(author, 20), truncate(category, 12),
                    availableCopies, totalCopies);
        }
    }

    static class Member {
        private final String memberId;
        private String name;
        private String email;
        private final LocalDate joinDate;
        private final List<Transaction> activeLoans = new ArrayList<>();
        static final int MAX_BOOKS = 3;

        Member(String memberId, String name, String email) {
            this.memberId = memberId;
            this.name = name;
            this.email = email;
            this.joinDate = LocalDate.now();
        }

        String getMemberId() { return memberId; }
        String getName() { return name; }
        String getEmail() { return email; }
        LocalDate getJoinDate() { return joinDate; }
        List<Transaction> getActiveLoans() { return activeLoans; }

        boolean canBorrow() { return activeLoans.size() < MAX_BOOKS; }

        @Override
        public String toString() {
            return String.format("%-8s | %-22s | %-28s | joined %s | %d book(s) borrowed",
                    memberId, truncate(name, 22), truncate(email, 28), joinDate, activeLoans.size());
        }
    }

    static class Transaction {
        private static int counter = 1000;
        private final int id;
        private final Book book;
        private final Member member;
        private final LocalDate issueDate;
        private final LocalDate dueDate;
        private LocalDate returnDate;
        private double fine;

        Transaction(Book book, Member member, int loanDays) {
            this.id = ++counter;
            this.book = book;
            this.member = member;
            this.issueDate = LocalDate.now();
            this.dueDate = issueDate.plusDays(loanDays);
        }

        int getId() { return id; }
        Book getBook() { return book; }
        Member getMember() { return member; }
        LocalDate getDueDate() { return dueDate; }
        boolean isReturned() { return returnDate != null; }
        double getFine() { return fine; }

        void close(LocalDate returned, double finePerDay) {
            this.returnDate = returned;
            long overdueDays = ChronoUnit.DAYS.between(dueDate, returned);
            this.fine = overdueDays > 0 ? overdueDays * finePerDay : 0;
        }

        @Override
        public String toString() {
            return String.format("#%d | %-25s | %-15s | issued %s | due %s | %s",
                    id, truncate(book.getTitle(), 25), truncate(member.getName(), 15),
                    issueDate, dueDate,
                    isReturned() ? "returned " + returnDate + (fine > 0 ? String.format(" (fine Rs.%.2f)", fine) : "")
                            : "NOT RETURNED");
        }
    }

    // ===================== LIBRARY (BUSINESS LOGIC) =====================

    static class Library {
        static final int LOAN_DAYS = 14;
        static final double FINE_PER_DAY = 5.0;

        private final Map<String, Book> books = new LinkedHashMap<>();
        private final Map<String, Member> members = new LinkedHashMap<>();
        private final List<Transaction> transactions = new ArrayList<>();
        private int memberCounter = 100;

        // ---- Books ----
        void addBook(String isbn, String title, String author, String category, int copies) {
            Book existing = books.get(isbn);
            if (existing != null) {
                existing.addCopies(copies);
                System.out.println("Book already exists. Added " + copies + " more copies.");
            } else {
                books.put(isbn, new Book(isbn, title, author, category, copies));
                System.out.println("Book added successfully.");
            }
        }

        void removeBook(String isbn) {
            Book b = books.get(isbn);
            if (b == null) {
                System.out.println("Book not found.");
            } else if (b.borrowedCopies() > 0) {
                System.out.println("Cannot remove: some copies are currently borrowed.");
            } else {
                books.remove(isbn);
                System.out.println("Book removed.");
            }
        }

        List<Book> searchBooks(String keyword) {
            String k = keyword.toLowerCase();
            return books.values().stream()
                    .filter(b -> b.getTitle().toLowerCase().contains(k)
                            || b.getAuthor().toLowerCase().contains(k)
                            || b.getCategory().toLowerCase().contains(k)
                            || b.getIsbn().toLowerCase().contains(k))
                    .collect(Collectors.toList());
        }

        Collection<Book> allBooks() { return books.values(); }

        // ---- Members ----
        Member registerMember(String name, String email) {
            String id = "M" + (++memberCounter);
            Member m = new Member(id, name, email);
            members.put(id, m);
            return m;
        }

        Collection<Member> allMembers() { return members.values(); }
        Member getMember(String id) { return members.get(id.toUpperCase()); }

        // ---- Issue / Return ----
        void issueBook(String memberId, String isbn) {
            Member m = getMember(memberId);
            Book b = books.get(isbn);

            if (m == null) { System.out.println("Member not found."); return; }
            if (b == null) { System.out.println("Book not found."); return; }
            if (!b.isAvailable()) { System.out.println("Sorry, no copies available right now."); return; }
            if (!m.canBorrow()) {
                System.out.println("Borrow limit reached (" + Member.MAX_BOOKS + " books). Return a book first.");
                return;
            }
            boolean alreadyHas = m.getActiveLoans().stream().anyMatch(t -> t.getBook().getIsbn().equals(isbn));
            if (alreadyHas) { System.out.println("Member already has a copy of this book."); return; }

            b.checkOut();
            Transaction t = new Transaction(b, m, LOAN_DAYS);
            m.getActiveLoans().add(t);
            transactions.add(t);
            System.out.println("Issued '" + b.getTitle() + "' to " + m.getName() + ". Due date: " + t.getDueDate());
        }

        void returnBook(String memberId, String isbn) {
            Member m = getMember(memberId);
            if (m == null) { System.out.println("Member not found."); return; }

            Optional<Transaction> opt = m.getActiveLoans().stream()
                    .filter(t -> t.getBook().getIsbn().equals(isbn))
                    .findFirst();
            if (opt.isEmpty()) { System.out.println("This member has not borrowed that book."); return; }

            Transaction t = opt.get();
            t.close(LocalDate.now(), FINE_PER_DAY);
            t.getBook().checkIn();
            m.getActiveLoans().remove(t);

            System.out.println("Returned '" + t.getBook().getTitle() + "'.");
            if (t.getFine() > 0) {
                System.out.printf("Book was overdue. Fine to pay: Rs.%.2f%n", t.getFine());
            } else {
                System.out.println("Returned on time. No fine.");
            }
        }

        List<Transaction> history() { return transactions; }
    }

    // ===================== CONSOLE UI =====================

    private static final Scanner sc = new Scanner(System.in);
    private static final Library library = new Library();

    public static void main(String[] args) {
        seedData();
        System.out.println("=========================================");
        System.out.println("      LIBRARY MANAGEMENT SYSTEM");
        System.out.println("=========================================");

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Enter choice: ");
            System.out.println();
            switch (choice) {
                case 1 -> addBook();
                case 2 -> removeBook();
                case 3 -> searchBooks();
                case 4 -> listBooks();
                case 5 -> registerMember();
                case 6 -> listMembers();
                case 7 -> issueBook();
                case 8 -> returnBook();
                case 9 -> memberLoans();
                case 10 -> showHistory();
                case 0 -> { running = false; System.out.println("Goodbye!"); }
                default -> System.out.println("Invalid choice. Try again.");
            }
        }
        sc.close();
    }

    private static void printMenu() {
        System.out.println("\n---------------- MENU ----------------");
        System.out.println(" 1. Add book              6. List members");
        System.out.println(" 2. Remove book           7. Issue book");
        System.out.println(" 3. Search books          8. Return book");
        System.out.println(" 4. List all books        9. View member's loans");
        System.out.println(" 5. Register member      10. Transaction history");
        System.out.println(" 0. Exit");
        System.out.println("--------------------------------------");
    }

    private static void addBook() {
        String isbn = readLine("ISBN: ");
        String title = readLine("Title: ");
        String author = readLine("Author: ");
        String category = readLine("Category: ");
        int copies = readInt("Number of copies: ");
        if (isbn.isEmpty() || title.isEmpty() || copies <= 0) {
            System.out.println("Invalid input. ISBN/title required and copies must be > 0.");
            return;
        }
        library.addBook(isbn, title, author, category, copies);
    }

    private static void removeBook() {
        library.removeBook(readLine("ISBN of book to remove: "));
    }

    private static void searchBooks() {
        String kw = readLine("Search (title/author/category/ISBN): ");
        List<Book> results = library.searchBooks(kw);
        if (results.isEmpty()) System.out.println("No books found.");
        else results.forEach(System.out::println);
    }

    private static void listBooks() {
        if (library.allBooks().isEmpty()) { System.out.println("No books in the library."); return; }
        library.allBooks().forEach(System.out::println);
    }

    private static void registerMember() {
        String name = readLine("Name: ");
        String email = readLine("Email: ");
        if (name.isEmpty()) { System.out.println("Name is required."); return; }
        Member m = library.registerMember(name, email);
        System.out.println("Member registered. Member ID: " + m.getMemberId());
    }

    private static void listMembers() {
        if (library.allMembers().isEmpty()) { System.out.println("No members registered."); return; }
        library.allMembers().forEach(System.out::println);
    }

    private static void issueBook() {
        library.issueBook(readLine("Member ID: "), readLine("Book ISBN: "));
    }

    private static void returnBook() {
        library.returnBook(readLine("Member ID: "), readLine("Book ISBN: "));
    }

    private static void memberLoans() {
        Member m = library.getMember(readLine("Member ID: "));
        if (m == null) { System.out.println("Member not found."); return; }
        if (m.getActiveLoans().isEmpty()) { System.out.println(m.getName() + " has no borrowed books."); return; }
        System.out.println("Books currently borrowed by " + m.getName() + ":");
        m.getActiveLoans().forEach(System.out::println);
    }

    private static void showHistory() {
        if (library.history().isEmpty()) { System.out.println("No transactions yet."); return; }
        library.history().forEach(System.out::println);
    }

    // ===================== HELPERS =====================

    private static String readLine(String prompt) {
        System.out.print(prompt);
        return sc.nextLine().trim();
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static void seedData() {
        library.addBook("9780134685991", "Effective Java", "Joshua Bloch", "Programming", 3);
        library.addBook("9780132350884", "Clean Code", "Robert C. Martin", "Programming", 2);
        library.addBook("9780596009205", "Head First Java", "Kathy Sierra", "Programming", 2);
        library.addBook("9780061120084", "To Kill a Mockingbird", "Harper Lee", "Fiction", 1);
        library.registerMember("Demo User", "demo@example.com");
        System.out.println("(Sample data loaded: 4 books and member M101)\n");
    }
}
