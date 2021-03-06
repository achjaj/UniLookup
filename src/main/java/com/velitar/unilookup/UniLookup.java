package com.velitar.unilookup;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lookup table
 * @author Velitar
 * @version 0.1
 */
public class UniLookup {
    public static final String VERSION = "0.1";

    private final HashMap<String, String> groupsAcronymsMap;
    private final List<String> blocks;
    private final Statement statement;
    private final File tmpResources;

    private final boolean debug = false;

    /**
     * Parse csv table with groups acronyms and their names and creates connection to SQLite database.
     * @throws IOException thrown when parsing csv failed
     * @throws SQLException thrown when creating connection failed
     */
    public UniLookup(File tmpResources) throws IOException, SQLException {
        this.tmpResources = tmpResources;
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + getResource("symbols.db"));
        statement = connection.createStatement();

        groupsAcronymsMap = loadGroups();
        blocks = loadBlocks();
    }

    /**
     * Default constructor with tmpResources set to System.getProperty("user.home")/.unilookup
     * @throws IOException
     * @throws SQLException
     */
    public UniLookup() throws IOException, SQLException {
        this(new File(System.getProperty("user.home"), ".unilookup"));
    }

    /**
     * Convert int or char into correct {@link Symbol} value.
     * @param i symbol char/int
     * @return correct value
     */
    public static String intToValue(int i) {
        String value = Integer.toHexString(i).toUpperCase();
        return value.length() == 4 ? value : "0" + value;
    }

    private List<String> loadBlocks() throws IOException {
        return Files.readAllLines(Paths.get(getResource("blocks")));
    }

    private void createTmpDir() throws IOException {
        if (!tmpResources.mkdir())
            throw new IOException("Cannot create resources folder!");
    }

    private String getResource(String name) throws IOException {
        if (!tmpResources.exists() || !tmpResources.isDirectory())
            createTmpDir();

        File resourceFile = new File(tmpResources, name);
        if (!resourceFile.exists() || !resourceFile.isFile())
            unpack(name, resourceFile.toPath());

        return resourceFile.getAbsolutePath();
    }

    private void unpack(String name, Path destination) throws IOException {
        System.out.println("[UniLookup] Extracting " + name);
        InputStream is = getClass().getClassLoader().getResourceAsStream(name);

        Files.copy(is, destination);
    }

    private void deleteResource(String name) throws IOException {
        File rf = new File(tmpResources, name);
        if (rf.exists() && !rf.delete())
            throw new IOException("Cannot delete " + name);
    }

    private Symbol parseSymbol(ResultSet set) throws SQLException {
        return new Symbol(set.getString("value"),
                          set.getString("name"),
                          set.getString("groupName"),
                          set.getString("block"),
                          set.getInt("emoji") == 1);
    }

    private HashMap<String, String> loadGroups() throws IOException {
        HashMap<String, String> result = new HashMap<>();

        Files.readAllLines(Paths.get(getResource("groups.csv"))).stream()
                .map(line -> line.split(";"))
                .forEach(pair -> result.put(pair[0], pair[1]));

        return result;
    }

    private List<Symbol> queryStreamToList(Stream<List<Symbol>> stream) {
        return stream == null ? new ArrayList<>():
                stream.filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
    }

    private List<Symbol> parseQuery(ResultSet query) throws SQLException {
        ArrayList<Symbol> list = new ArrayList<>();
        while (query.next())
            list.add(parseSymbol(query));

        return list.size() > 0 ? list : null;
    }

    public String getGeneratorVersion() throws SQLException {
        return executeQuery("SELECT gVersion FROM meta").getString(0);
    }

    public String getUnicodeVersion() throws SQLException {
        return executeQuery("SELECT uVersion FROM meta").getString(0);
    }

    public String getFlavour() throws SQLException {
        return executeQuery("SELECT flavour FROM meta").getString(0);
    }

    /**
     * Returns acronym for full group name.
     * Example: getGroupAcronym("Control") returns "Cc"
     * @param groupName full group name
     * @return {@link String} with group acronym or null if there is no such name
     */
    public String getGroupAcronym(String groupName) {
        return groupsAcronymsMap.entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(groupName))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Get names of all groups
     * @return array of String of all names
     */
    public String[] getGroupsNames() {
        return groupsAcronymsMap.values().toArray(new String[]{});
    }

    /**
     * Returns full group name for given acronym.
     * Example: getGroupName("Cc") returns "Control"
     * @param acronym group acronym
     * @return {@link String} with full group name or null if there is no such acronym
     */
    public String getGroupName(String acronym) {
        return groupsAcronymsMap.getOrDefault(acronym, null);
    }

    /**
     * Returns set of all acronyms.
     * @return array of Strings of all acronyms
     */
    public String[] getGroupsAcronyms() {
        return groupsAcronymsMap.keySet().toArray(new String[]{});
    }

    private void handleExceptionPrinting(SQLException e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);

        if (!sw.toString().contains("no such table"))
            e.printStackTrace();
    }

    /**
     * Get {@link Symbol}s from given group.
     * @param group group acronym
     * @return {@link List} of {@link Symbol}s or null if there is no such group
     */
    public List<Symbol> getGroup(String group) {
        try {
            ResultSet query = executeQuery("SELECT * FROM " + group);

            return parseQuery(query);
        } catch (SQLException e) {
            handleExceptionPrinting(e);
            return null;
        }
    }

    /**
     * Get {@link Symbol}s with given name from given group.
     * @param name symbol/s name
     * @param group group acronym
     * @return {@link List} of {@link Symbol}s or null if there is no such group or name
     */
    public List<Symbol> getByNameFromGroup(String name, String group) {
        try {
            ResultSet query = executeQuery(String.format("SELECT * FROM %s WHERE name='%s'", group, name.toUpperCase()));

            return parseQuery(query);
        } catch (SQLException e) {
            handleExceptionPrinting(e);
            return null;
        }
    }

    /**
     * Get {@link Symbol}s with given name from given array of groups.
     * @param name symbol/s name
     * @param groups array of groups acronyms
     * @return {@link List} of {@link Symbol}s or null if there is no such name
     */
    public List<Symbol> getByNameFromGroups(String name, String... groups) {
        List<Symbol> list = queryStreamToList(Arrays.stream(groups)
                .map(group -> getByNameFromGroup(name, group)));


        return list.size() > 0 ? list : null;
    }

    /**
     * Get {@link Symbol}s with given name.
     * @param name symbol/s name
     * @return {@link List} of {@link Symbol}s or null if there is no such name
     */
    public List<Symbol> getByName(String name) {
        return getByNameFromGroups(name, getGroupsAcronyms());
    }

    /**
     * Get {@link Symbol}s from given group witch contains nameSeq substring in their names.
     * @param nameSeq any substring
     * @param group group acronym
     * @return {@link List} of {@link Symbol}s or null if there is no such symbol or group
     */
    public List<Symbol> findNameSeqInGroup(String nameSeq, String group) {
        try {
            ResultSet query = executeQuery(String.format("SELECT * FROM %s WHERE name LIKE '%%%s%%'", group, nameSeq.toUpperCase()));

            return parseQuery(query);
        } catch (SQLException e) {
            handleExceptionPrinting(e);
            return null;
        }
    }

    /**
     * Get {@link Symbol}s from given groups witch contains nameSeq substring in their names.
     * @param nameSeq any substring
     * @param groups array of groups acronyms
     * @return {@link List} of {@link Symbol}s or null if there is no such symbol
     */
    public List<Symbol> findNameSeqInGroups(String nameSeq, String... groups) {
        List<Symbol> list = queryStreamToList(Arrays.stream(groups)
                .map(group -> findNameSeqInGroup(nameSeq, group)));

        return list.size() > 0 ? list : null;
    }

    /**
     * Get {@link Symbol}s with contains nameSeq substring in their names.
     * @param nameSeq any substring
     * @return {@link List} of {@link Symbol}s or null if there is no such symbol
     */
    public List<Symbol> findNameSeq(String nameSeq) {
        return findNameSeqInGroups(nameSeq, getGroupsAcronyms());
    }

    /**
     * Get {@link Symbol} with given value from given groups.
     * @param value string with value
     * @param groups array of groups acronyms
     * @return {@link Symbol} or null if there is no such symbol
     */
    public Symbol getByValueFromGroups(String value, String... groups) {
        return Arrays.stream(groups)
                .map(group -> getByValueFromGroup(value, group))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    /**
     * Get {@link Symbol} with given value from given group.
     * @param value string with value
     * @param group group acronym
     * @return {@link Symbol} or null if there is no such symbol
     */
    public Symbol getByValueFromGroup(String value, String group) {
        try {
            ResultSet query = executeQuery(String.format("SELECT * FROM %s WHERE value='%s'", group, value.toUpperCase()));
            List<Symbol> list = parseQuery(query);

            return list != null ? list.get(0) : null;
        } catch (SQLException e) {
            handleExceptionPrinting(e);
            return null;
        }
    }

    /**
     * Get {@link Symbol} with given value.
     * @param value string with value
     * @return {@link Symbol} or null if there is no such symbol
     */
    public Symbol getByValue(String value) {
        return getByValueFromGroups(value, getGroupsAcronyms());
    }

    /**
     * Get all {@link Symbol}s.
     * DO NOT CALL .toString() ON THE LIST! There is too much of them and it will probably freeze your PC.
     * @return {@link List} of all {@link Symbol}s
     */
    public List<Symbol> getSymbols() {
        return queryStreamToList(
                Arrays.stream(getGroupsAcronyms())
                .map(this::getGroup)
        );
    }

    /**
     * Get all {@link Symbol}s which are marked as emoji.
     * @return {@link List} of all {@link Symbol}s marked as emoji.
     */
    public List<Symbol> getEmojis() {
        return getSymbols().stream()
                .filter(symbol -> symbol.emoji)
                .collect(Collectors.toList());
    }

    /**
     * Get {@link Symbol}s from given block.
     * @param block block name
     * @return {@link List} of all {@link Symbol}s or null if there is no such block
     */
    public List<Symbol> getBlock(String block) {
        List<Symbol> list = getSymbols().stream()
                .filter(symbol -> symbol.block.equals(block))
                .collect(Collectors.toList());

        return list.size() > 0 ? list : null;
    }

    /**
     * Execute SQLite query.
     * @param cmd query
     * @return {@link ResultSet}
     * @throws SQLException thrown if it fails
     */
    public ResultSet executeQuery(String cmd) throws SQLException {
        if (debug) System.out.printf("Executing query: %s%n", cmd);

        return statement.executeQuery(cmd);
    }

    /**
     * Delete extracted DB file
     * @throws IOException
     */
    public void deleteDBFile() throws IOException {
        deleteResource("symbols.db");
    }

    /**
     * Delete extracted groups acronyms csv
     * @throws IOException
     */
    public void deleteGroupsFile() throws IOException {
        deleteResource("groups.csv");
    }

    /**
     * Delete all extracted files and parent directory
     * @throws IOException
     */
    public void deleteResourcesDirectory() throws IOException {
        deleteDBFile();
        deleteGroupsFile();

        if (!tmpResources.delete())
            throw new IOException("Cannot delete resources directory");
    }

    /**
     * Get names of all blocks
     * @return array of Strings with names
     */
    public String[] getBlocks() {
        return blocks.toArray(new String[]{});
    }

    /**
     * Get symbol with specified value from specified block
     * @param value symbol value
     * @param block name of block
     * @return symbol orr null if there is no such symbol or block
     */
    public Symbol getByValueFromBlock(String value, String block) {
        List<Symbol> symbols = getBlock(block);
        if (symbols == null)
            return null;

        return symbols.stream()
                .filter(symbol -> symbol.value.equals(value))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get symbol with specified value from list og blocks
     * @param value symbol value
     * @param blocks array of blocks names
     * @return symbol or null if tehere is no such symbol
     */
    public Symbol getByValueFromBlocks(String value, String... blocks) {
        return Arrays.stream(blocks)
                .map(block -> getByValueFromBlock(value, block))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get symbols from block with have seq in their names
     * @param seq any substring
     * @param block block name
     * @return list of symbols
     */
    public List<Symbol> findNameSeqInBlock(String seq, String block) {
        List<Symbol> symbols = getBlock(block);
        if (symbols == null)
            return null;

        return symbols.stream()
                .filter(symbol -> symbol.name.contains(seq))
                .collect(Collectors.toList());

    }

    /**
     * Get symbols from blocks which have seq in their names
     * @param seq any substring
     * @param blocks array of block names
     * @return list of symbols
     */
    public List<Symbol> findNameSeqInBlocks(String seq, String... blocks) {
        return queryStreamToList(Arrays.stream(blocks)
                .map(block -> findNameSeqInBlock(seq, block)));
    }

    /**
     * Get symbols with specified name from specified block
     * @param name symbols name
     * @param block block name
     * @return list of symbols
     */
    public List<Symbol> getByNameFromBlock(String name, String block) {
        return getBlock(block).stream()
                .filter(symbol -> symbol.name.equals(name))
                .collect(Collectors.toList());
    }

    /**
     * get symbols with specified name from specified blocks
     * @param name symbols name
     * @param blocks array of block names
     * @return list of symbols
     */
    public List<Symbol> getByNameFromBlocks(String name, String... blocks) {
        return queryStreamToList(Arrays.stream(blocks)
                .map(block -> getByNameFromBlock(name, block)));

    }

}
