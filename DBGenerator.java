import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DBGenerator extends DefaultHandler {
    private final Connection connection;
    private final Statement statement;
    private final boolean unihan;

    public DBGenerator(boolean unihan) throws SQLException, IOException {
        this.unihan = unihan;

        connection = DriverManager.getConnection("jdbc:sqlite:" + getResource("symbols.db"));
        statement = connection.createStatement();

        var groups = getValidAcronyms();

        for (var group : groups) {
            statement.execute("DROP TABLE IF EXISTS " + group);
            statement.execute(String.format("CREATE TABLE %s (value TEXT, name TEXT, groupName TEXT, block TEXT, emoji INTEGER)", group));
        }
    }

    private String getResource(String name) {
        return "src/main/resources/" + name;
    }

    private String[] getValidAcronyms() throws IOException {
        if (unihan)
            return new String[] {"unihan"};

        return Files.readAllLines(Path.of(getResource("groups.csv")))
                .stream()
                .map(line -> line.split(";")[0])
                .toArray(String[]::new);
    }

    private String getStringAttribute(String name, Attributes attributes) {
        var str = attributes.getValue(name);
        return str == null ? "<NULL>" : str;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        super.startElement(uri, localName, qName, attributes);

        if (qName.equals("char")) {
            var group = unihan ? "unihan" : getStringAttribute("gc", attributes);
            var value = getStringAttribute("cp", attributes);

            var name = attributes.getValue("na");
            if (name == null || name.isEmpty())
                name = getStringAttribute("na1", attributes);

            var block = getStringAttribute("blk", attributes);

            var emojiStr = attributes.getValue("Emoji");
            int emoji;
            if (emojiStr == null)
                emoji = 0;
            else
                emoji = emojiStr.toLowerCase().equals("y") ? 1 : 0;

            var cmd = String.format("INSERT INTO %s(value, name, groupName, block, emoji)" +
                                    "VALUES('%s', '%s', '%s', '%s', %d)", group, value, name, group, block, emoji);

            try {
                statement.execute(cmd);
            } catch (SQLException e) {
                e.printStackTrace();
            }

            //System.out.printf("Parsed: U+%s %s from group %s%n", attributes.getValue("cp"), name, group);
        }

    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();

        try {
            statement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void main(String... args) throws Exception {
        if (args.length < 1 || !args[0].matches("all|unihan|nounihan")) {
            System.err.println("Usage: dbgenerator unihan|nounihan|all");
            System.exit(1);
        }

        var base = "ucd." + args[0] + ".flat";
        var source = "http://www.unicode.org/Public/UCD/latest/ucdxml/" + base + ".zip";
        var zipPath = Path.of("unicode.zip");

        if (!Files.exists(zipPath) || !Files.isRegularFile(zipPath)) {
            System.out.println("Downloading zip: " + source);
            download(source, zipPath);
        }

        var xmlPath = Path.of(base + ".xml");
        if (!Files.exists(xmlPath) || !Files.isRegularFile(xmlPath)) {
            System.out.println("Extracting");
            unzip(zipPath, base + ".xml");
        }

        System.out.println("Generating DB");
        System.out.println("This may take a while.");
        SAXParserFactory.newInstance().newSAXParser().parse(xmlPath.toFile(), new DBGenerator(args[0].equals("unihan")));

        System.out.println("Modifying sources");
        setVariant(args[0]);
    }

    private static void download(String source, Path to) throws IOException {
        try(var channel = Channels.newChannel(new URL(source).openStream());
            var output = new FileOutputStream(to.toFile())) {
            output.getChannel().transferFrom(channel, 0, Long.MAX_VALUE);
        }
    }

    private static void unzip(Path source, String name) throws IOException {
        try (var fs = FileSystems.newFileSystem(source, null)) {
            var zipSource = fs.getPath(name);
            Files.copy(zipSource, Paths.get(name));
        }
    }

    private static void setVariant(String variant) throws IOException {
        var path = Path.of("src/main/java/com/velitar/unilookup/UniLookup.java");
        Files.writeString(path, Files.readString(path).replace("%var%", variant));
    }
}
