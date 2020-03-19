package com.velitar.unilookup;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Velitar
 * @version 0.1a
 */
public class UniLookup {
    public static final String VERSION = "0.1a";
    public static final String VARIANT = "%var%";

    private final HashMap<String, String> groupsAcronymsMap;
    private final String baseFolder;
    private final Statement statement;

    private final boolean debug = false;

    /**
     * Parse csv table with groups acronyms and their names and creates connection to SQLite database.
     * @throws IOException thrown when parsing csv failed
     * @throws SQLException thrown when creating connection failed
     */
    public UniLookup() throws IOException, SQLException {
        this.baseFolder = "../../../resources";
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + getResource("symbols.db"));
        statement = connection.createStatement();

        groupsAcronymsMap = loadGroups();
    }

    private String getResource(String name) {
        return getClass().getClassLoader().getResource(name).toExternalForm().replace("file:", "");
    }

    private HashMap<String, String> loadGroups() throws IOException {
        var result = new HashMap<String, String>();

        Files.readAllLines(Paths.get(getResource("groups.csv"))).stream()
                .map(line -> line.split(";"))
                .forEach(pair -> result.put(pair[0], pair[1]));

        return result;
    }

    private Symbol parseSymbol(ResultSet set) throws SQLException {
        return new Symbol(set.getString("value"),
                          set.getString("name"),
                          set.getString("groupName"),
                          set.getString("block"),
                          set.getInt("emoji") == 1);
    }

    private List<Symbol> parseQuery(ResultSet query) throws SQLException {
        var list = new ArrayList<Symbol>();
        while (query.next())
            list.add(parseSymbol(query));

        return list.size() > 0 ? list : null;
    }

    private List<Symbol> queryStreamToList(Stream<List<Symbol>> stream) {
        return stream == null ? new ArrayList<>():
                stream.filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
    }

    private void handleExceptionPrinting(SQLException e) {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        e.printStackTrace(pw);

        if (!sw.toString().contains("no such table"))
            e.printStackTrace();
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
     * @return {@link Set<String>} of all acronyms
     */
    public Set<String> getGroupsAcronyms() {
        return groupsAcronymsMap.keySet();
    }

    /**
     * Get {@link Symbol}s from given group.
     * @param group group acronym
     * @return {@link List<Symbol>} of {@link Symbol}s or null if there is no such group
     */
    public List<Symbol> getGroup(String group) {
        try {
            var query = executeQuery("SELECT * FROM " + group);

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
     * @return {@link List<Symbol>} of {@link Symbol}s or null if there is no such group or name
     */
    public List<Symbol> getByNameFromGroup(String name, String group) {
        try {
            var query = executeQuery(String.format("SELECT * FROM %s WHERE name='%s'", group, name.toUpperCase()));

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
     * @return {@link List<Symbol>} of {@link Symbol}s or null if there is no such name
     */
    public List<Symbol> getByNameFromGroups(String name, String... groups) {
        var list = queryStreamToList(Arrays.stream(groups)
                .map(group -> getByNameFromGroup(name, group)));


        return list.size() > 0 ? list : null;
    }

    /**
     * Get {@link Symbol}s with given name.
     * @param name symbol/s name
     * @return {@link List<Symbol>} of {@link Symbol}s or null if there is no such name
     */
    public List<Symbol> getByName(String name) {
        return getByNameFromGroups(name, getValidGroupsAcronyms().toArray(String[]::new));
    }

    /**
     * Get {@link Symbol}s from given group witch contains nameSeq substring in their names.
     * @param nameSeq any substring
     * @param group group acronym
     * @return {@link List<Symbol>} of {@link Symbol}s or null if there is no such symbol or group
     */
    public List<Symbol> findNameSeqInGroup(String nameSeq, String group) {
        try {
            var query = executeQuery(String.format("SELECT * FROM %s WHERE name LIKE '%%%s%%'", group, nameSeq.toUpperCase()));

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
     * @return {@link List<Symbol>} of {@link Symbol}s or null if there is no such symbol
     */
    public List<Symbol> findNameSeqInGroups(String nameSeq, String... groups) {
        var list = queryStreamToList(Arrays.stream(groups)
                .map(group -> findNameSeqInGroup(nameSeq, group)));

        return list.size() > 0 ? list : null;
    }

    /**
     * Get {@link Symbol}s with contains nameSeq substring in their names.
     * @param nameSeq any substring
     * @return {@link List<Symbol>} of {@link Symbol}s or null if there is no such symbol
     */
    public List<Symbol> findNameSeq(String nameSeq) {
        return findNameSeqInGroups(nameSeq, getValidGroupsAcronyms().toArray(String[]::new));
    }

    /**
     * Get {@link Symbol} with given value from given group.
     * @param value string with value
     * @param group group acronym
     * @return {@link Symbol} or null if there is no such symbol
     */
    public Symbol getByValueFromGroup(String value, String group) {
        try {
            var query = executeQuery(String.format("SELECT * FROM %s WHERE value='%s'", group, value.toUpperCase()));
            var list = parseQuery(query);

            return list != null ? list.get(0) : null;
        } catch (SQLException e) {
            handleExceptionPrinting(e);
            return null;
        }
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
     * Get {@link Symbol} with given value.
     * @param value string with value
     * @return {@link Symbol} or null if there is no such symbol
     */
    public Symbol getByValue(String value) {
        return getByValueFromGroups(value, getValidGroupsAcronyms().toArray(String[]::new));
    }

    /**
     * Get all {@link Symbol}s.
     * DO NOT CALL .toString() ON THE LIST! There is too much of them and it will probably freeze your PC.
     * @return {@link List<Symbol>} of all {@link Symbol}s
     */
    public List<Symbol> getSymbols() {
        return queryStreamToList(
                getValidGroupsAcronyms().stream()
                .map(this::getGroup)
        );
    }

    /**
     * Get {@link Symbol}s from given block.
     * @param block block name
     * @return {@link List<Symbol>} of all {@link Symbol}s or null if there is no such block
     */
    public List<Symbol> getBlock(String block) {
        var list = getSymbols().stream()
                .filter(symbol -> symbol.block.equals(block))
                .collect(Collectors.toList());

        return list.size() > 0 ? list : null;
    }

    /**
     * Get all {@link Symbol}s which are marked as emoji.
     * @return {@link List<Symbol>} of all {@link Symbol}s marked as emoji.
     */
    public List<Symbol> getEmojis() {
        return getSymbols().stream()
                .filter(symbol -> symbol.emoji)
                .collect(Collectors.toList());
    }

    /**
     * Get {@link Set<String>} with acronyms from table.
     * @return {@link Set<String>} with acronyms
     */
    public Set<String> getValidGroupsAcronyms() {
        return groupsAcronymsMap.keySet();
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
     * Convert int or char into correct {@link Symbol} value.
     * @param i symbol char/int
     * @return correct value
     */
    public static String intToValue(int i) {
        var value = Integer.toHexString(i).toUpperCase();
        return value.length() == 4 ? value : "0" + value;
    }

}