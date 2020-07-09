package com.velitar.unilookup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lookup table
 * @author Velitar
 * @version 0.1a
 */
public class UniLookup {
    public static final String VERSION = "0.1a";
    public final String flavour;
    public final String dbgVersion;
    private final HashMap<String, String> groupsAcronymsMap;
    private final List<String> blocks;
    private final boolean debug = false;
    private String root;
    private File tmp;
    private Cache cache;

    /**
     * Constructor
     * @throws IOException thrown when parsing csv failed
     */
    public UniLookup(String root, Cache.CacheSettings settings) throws IOException {
        this.root = root;
        tmp = new File(root, "tmp");
        groupsAcronymsMap = loadGroups();
        blocks = loadBlocks();

        cache = new Cache(root, settings, this);


        String[] metaInf = loadMeta();
        flavour = metaInf[0];
        dbgVersion = metaInf[1];


    }

    /**
     * Default constructor with root set to System.getProperty("user.home")/.unilookup
     * @throws IOException
     */
    public UniLookup() throws IOException{
        this(new File(System.getProperty("user.home"), ".unilookup").toString(), new Cache.CacheSettings());
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

    private String[] loadMeta() throws IOException {
        String[] meta = new String[2];
        Files.readAllLines(Paths.get(root, "meta")).forEach(line -> {
            String[] split = line.split("=");
            if (split[0].equals("flavour")) {
                meta[0] = split[1];
            } else {
                meta[1] = split[1];
            }
        });

        return meta;
    }

    private List<String> loadBlocks() throws IOException {
        return Files.readAllLines(Paths.get(getResource("blocks")));
    }

    private void createTmpDir() throws IOException {
        if (!tmp.mkdir())
            throw new IOException("Cannot create resources folder!");
    }

    private String getResource(String name) throws IOException {
        if (!tmp.exists() || !tmp.isDirectory())
            createTmpDir();

        File resourceFile = new File(tmp, name);
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
        File rf = new File(tmp, name);
        if (rf.exists() && !rf.delete())
            throw new IOException("Cannot delete " + name);
    }

    private HashMap<String, String> loadGroups() throws IOException {
        HashMap<String, String> result = new HashMap<>();

        Files.readAllLines(Paths.get(getResource("groups.csv"))).stream()
                .map(line -> line.split(";"))
                .forEach(pair -> result.put(pair[0], pair[1]));

        return result;
    }

    public void setCacheSettings(Cache.CacheSettings settings) throws IOException {
        cache.setSettings(settings);
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

    /**
     * Get {@link Symbol}s from given group.
     * @param group group acronym
     * @return {@link List} of {@link Symbol}s or null if there is no such group
     */
    public List<Symbol> getGroup(String group) throws IOException {
        JSONArray array = cache.getGroup(group).getJSONArray("symbols");
        ArrayList<Symbol> symbols = new ArrayList<>();

        for (Object o : array) {
            Symbol symbol = new Symbol((JSONObject) o);
            symbols.add(symbol);
        }

        return symbols;
    }

    /**
     * Get {@link Symbol}s with given name from given group.
     * @param name symbol/s name
     * @param group group acronym
     * @return {@link List} of {@link Symbol}s or null if there is no such group or name
     */
    public List<Symbol> getByNameFromGroup(String name, String group) throws IOException {
        List<Symbol> symbols = getGroup(group);

        return symbols.stream().filter(symbol -> symbol.name.equals(name)).collect(Collectors.toList());
    }

    /**
     * Get {@link Symbol}s with given name from given array of groups.
     * @param name symbol/s name
     * @param groups array of groups acronyms
     * @return {@link List} of {@link Symbol}s or null if there is no such name
     */
    public List<Symbol> getByNameFromGroups(String name, String... groups) throws IOException {
        List<Symbol> symbols = new ArrayList<>();
        for (String group : groups)
            symbols.addAll(getByNameFromGroup(name, group));

        return symbols;
    }

    /**
     * Get {@link Symbol}s with given name.
     * @param name symbol/s name
     * @return {@link List} of {@link Symbol}s or null if there is no such name
     */
    public List<Symbol> getByName(String name) throws IOException {
        return getByNameFromGroups(name, getGroupsAcronyms());
    }

    /**
     * Get {@link Symbol}s from given group witch contains nameSeq substring in their names.
     * @param nameSeq any substring
     * @param group group acronym
     * @return {@link List} of {@link Symbol}s or null if there is no such symbol or group
     */
    public List<Symbol> findNameSeqInGroup(String nameSeq, String group) throws IOException {
        List<Symbol> symbols = getGroup(group);
        return symbols.stream().filter(symbol -> symbol.name.toLowerCase().contains(nameSeq.toLowerCase())).collect(Collectors.toList());
    }

    /**
     * Get {@link Symbol}s from given groups witch contains nameSeq substring in their names.
     * @param nameSeq any substring
     * @param groups array of groups acronyms
     * @return {@link List} of {@link Symbol}s or null if there is no such symbol
     */
    public List<Symbol> findNameSeqInGroups(String nameSeq, String... groups) throws IOException {
        List<Symbol> symbols = new ArrayList<>();

        for(String group : groups)
            symbols.addAll(findNameSeqInGroup(nameSeq, group));

        return symbols;
    }

    /**
     * Get {@link Symbol}s witch contains nameSeq substring in their names.
     * @param nameSeq any substring
     * @return {@link List} of {@link Symbol}s or null if there is no such symbol
     */
    public List<Symbol> findNameSeq(String nameSeq) throws IOException {
        return findNameSeqInGroups(nameSeq, getGroupsAcronyms());
    }

    /**
     * Get {@link Symbol} with given value from given groups.
     * @param value string with value
     * @param groups array of groups acronyms
     * @return {@link Symbol} or null if there is no such symbol
     */
    public Symbol getByValueFromGroups(String value, String... groups) throws IOException {
        List<Symbol> symbols = new ArrayList<>();
        for (String group : groups) {
            symbols.add(getByValueFromGroup(value, group));
        }

        if (symbols.size() > 0)
            return symbols.get(0);

        return null;
    }

    /**
     * Get {@link Symbol} with given value from given group.
     * @param value string with value
     * @param group group acronym
     * @return {@link Symbol} or null if there is no such symbol
     */
    public Symbol getByValueFromGroup(String value, String group) throws IOException {
        List<Symbol> symbols = getGroup(group);
        return symbols.stream().filter(symbol -> symbol.value.equals(value)).findFirst().get();
    }

    /**
     * Get {@link Symbol} with given value.
     * @param value string with value
     * @return {@link Symbol} or null if there is no such symbol
     */
    public Symbol getByValue(String value) throws IOException {
        return getByValueFromGroups(value, getGroupsAcronyms());
    }

    /**
     * Get all {@link Symbol}s.
     * DO NOT CALL .toString() ON THE LIST! There is too much of them and it will probably freeze your PC.
     * @return {@link List} of all {@link Symbol}s
     */
    public List<Symbol> getSymbols() throws IOException {
        List<Symbol> symbols = new ArrayList<>();
        for (String group : getGroupsAcronyms()) {
            symbols.addAll(getGroup(group));
        }

        return symbols;
    }

    /**
     * Get all {@link Symbol}s which are marked as emoji.
     * @return {@link List} of all {@link Symbol}s marked as emoji.
     */
    public List<Symbol> getEmojis() throws IOException {
        return getSymbols().stream()
                .filter(symbol -> symbol.emoji)
                .collect(Collectors.toList());
    }

    /**
     * Get {@link Symbol}s from given block.
     * @param block block name
     * @return {@link List} of all {@link Symbol}s or null if there is no such block
     */
    public List<Symbol> getBlock(String block) throws IOException {
        List<Symbol> list = getSymbols().stream()
                .filter(symbol -> symbol.block.equals(block))
                .collect(Collectors.toList());

        return list.size() > 0 ? list : null;
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

        if (!tmp.delete())
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
    public Symbol getByValueFromBlock(String value, String block) throws IOException {
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
    public Symbol getByValueFromBlocks(String value, String... blocks) throws IOException {
        for (String block : blocks) {
            Symbol symbol = getByValueFromBlock(value, block);
            if (symbol != null)
                return symbol;
        }

        return null;
    }

    /**
     * Get symbols from block with have seq in their names
     * @param seq any substring
     * @param block block name
     * @return list of symbols
     */
    public List<Symbol> findNameSeqInBlock(String seq, String block) throws IOException {
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
     * @return list of symbols, WARNING: may contains null-s
     */
    public List<Symbol> findNameSeqInBlocks(String seq, String... blocks) throws IOException {
        List<Symbol> symbols = new ArrayList<>();

        for (String block : blocks)
            symbols.addAll(findNameSeqInBlock(seq, block));

        return symbols;
    }

    /**
     * Get symbols with specified name from specified block
     * @param name symbols name
     * @param block block name
     * @return list of symbols
     */
    public List<Symbol> getByNameFromBlock(String name, String block) throws IOException {
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
    public List<Symbol> getByNameFromBlocks(String name, String... blocks) throws IOException {
        List<Symbol> symbols = new ArrayList<>();

        for (String block : blocks)
            symbols.addAll(getByNameFromBlock(name, block));

        return symbols;

    }

}
