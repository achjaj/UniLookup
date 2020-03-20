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
 * @version 0.1a
 */
public class UniLookup {
    public static final String VERSION = "0.1a";
    public static final String VARIANT = "%all%";

    private final HashMap<String, String> groupsAcronymsMap;
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
     * @return {@link Set} of all acronyms
     */
    public Set<String> getGroupsAcronyms() {
        return groupsAcronymsMap.keySet();
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
        return getByNameFromGroups(name, getValidGroupsAcronyms());
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
        return findNameSeqInGroups(nameSeq, getValidGroupsAcronyms());
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
        return getByValueFromGroups(value, getValidGroupsAcronyms());
    }

    /**
     * Get all {@link Symbol}s.
     * DO NOT CALL .toString() ON THE LIST! There is too much of them and it will probably freeze your PC.
     * @return {@link List} of all {@link Symbol}s
     */
    public List<Symbol> getSymbols() {
        return queryStreamToList(
                Arrays.stream(getValidGroupsAcronyms())
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
     * Get {@link Set} with acronyms from table.
     * @return {@link Set} with acronyms
     */
    public String[] getValidGroupsAcronyms() {
        String[] strs = new String[]{};
        return groupsAcronymsMap.keySet().toArray(strs);
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

}
