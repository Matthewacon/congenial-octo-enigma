package org.coe;

import com.flowpowered.nbt.*;
import com.flowpowered.nbt.stream.NBTInputStream;
import com.flowpowered.nbt.stream.NBTOutputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.flowpowered.nbt.TagType.*;

final class RuntimeException extends java.lang.RuntimeException {
    public RuntimeException setCause(Exception e) {
        initCause(e);
        return this;
    }
}

final class ItemContainer implements Comparable<ItemContainer> {
    final String unlocalizedName;
    final short id;
    public ItemContainer(String unlocalizedName, short id) {
        this.unlocalizedName = unlocalizedName;
        this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
        ItemContainer ic;
        try {
            ic = (ItemContainer)obj;
        } catch (ClassCastException e) {
            return false;
        }
        return (unlocalizedName.equals(ic.unlocalizedName))&&(id==ic.id);
    }

    @Override
    public int compareTo(ItemContainer ic1) {
        return Math.max(ic1.id, this.id) - Math.min(ic1.id, this.id);
    }
}

final class ItemMapContainer {
    public static final String INVALID_DIR = "Could not find directory: 'DIR'!";

    private static final class CSVParserThread extends Thread {
        String csvDir;
        CSVParser parser;
        CSVFormat format;
        LinkedHashMap<String, Class<?>> headerFormat;
        final HashMap<String, TreeSet<ItemContainer>> itemMap = new HashMap<>();

        public synchronized CSVParserThread init(String csvDir, LinkedHashMap<String, Class<?>> headerFormat)
        throws IOException
        {
            this.csvDir = csvDir;
            this.headerFormat = headerFormat;
            this.format = CSVFormat.DEFAULT.withHeader(headerFormat.keySet().toArray(new String[0]));
            this.parser = new CSVParser(new FileReader(csvDir), format);
//            System.out.println("Worker["+csvDir+"]: Starting at "+System.currentTimeMillis());
            super.start();
            return this;
        }

        @Override
        public void run() {
            List<CSVRecord> records;
            try {
                records = parser.getRecords();
            } catch (IOException e) { throw new RuntimeException().setCause(e); }
            int recordCount = records.size();
            final float chunkSize = 4.0f;
            final double blkSize = Math.floor(recordCount / chunkSize);
            //Skip header
            for (int i=1; i<recordCount; i++) {
                CSVRecord record = records.get(i);
                String modName = record.get("Name"), itemName = record.get("Class");
                if (modName.contains(":"))
                    modName = modName.substring(0, modName.lastIndexOf(":"));

                ItemContainer container = new ItemContainer(itemName, Short.parseShort(record.get("ID")));
                if (itemMap.containsKey(modName)) {
//                    System.out.println("Existing mod entry found: '"+ modName +"'");
                    itemMap.get(modName).add(container);
                } else {
//                    System.out.println("Adding new mod entry: '" + modName + "'");
                    TreeSet<ItemContainer> set = new TreeSet<>();
                    set.add(container);
                    itemMap.put(modName, set);
                }
                if ((i%blkSize)==0) {
                    String fileName = csvDir.substring(csvDir.lastIndexOf("/")+1, csvDir.length());
                    System.out.println("'"+fileName+"': " + (int)(100*((i/blkSize)/chunkSize)) + "%");
                }
            }
            interrupt();
//            System.out.println("Worker["+csvDir+"]: Finished at " + System.currentTimeMillis());
        }
    }
    public static final LinkedHashMap<String, Class<?>>
        ITEM_HEADERS_AND_TYPES = new LinkedHashMap<String, Class<?>>() {{
            put("Name", String.class);
            put("ID", Number.class);
            put("Has Item", Boolean.class);
            put("Mod", String.class);
            put("Class", String.class);
        }},
        BLOCK_HEADERS_AND_TYPES = new LinkedHashMap<String, Class<?>>() {{
            put("Name", String.class);
            put("ID", Number.class);
            put("Has Block", Boolean.class);
            put("Mod", String.class);
            put("Class", String.class);
        }};

    final ConcurrentHashMap<String, TreeSet<ItemContainer>> itemMap = new ConcurrentHashMap<>();
    final String csvFilePath;

    public ItemMapContainer(String csvDir) throws IOException {
        for (String dir : new String[] {csvDir, csvDir+"block.csv", csvDir+"item.csv"}) {
            if (!new File(dir).exists()) throw new IOException(INVALID_DIR.replace("DIR", dir));
        }

        this.csvFilePath = csvDir;
    }

    public void assembleItemMap() throws IOException {
        CSVParserThread[] waitOnThese = new CSVParserThread[] {
                new CSVParserThread().init(csvFilePath+"item.csv", ITEM_HEADERS_AND_TYPES),
                new CSVParserThread().init(csvFilePath+"block.csv", BLOCK_HEADERS_AND_TYPES)
        };
        Outer: while (true) {
            try {
                Thread.currentThread().sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException().setCause(e);
            }
            for (Thread waitOnMe : waitOnThese) {
                if (waitOnMe.isAlive()) continue Outer;
            }
            break;
        }
        for (CSVParserThread thread : waitOnThese) itemMap.putAll(thread.itemMap);
    }
}

public class Main {
    static final String PWD = System.getProperty("user.dir")+"/";
    static final String oldDataDir = PWD+"world/playerdata/";
    static final String oldCSVDir = PWD+"oldCSV/";
    static final String newDataDir = PWD+"world/playerdata/";
    static final String newCSVDir = PWD+"newCSV/";
    static final String[] playerFiles = new String[] {
            "f992bc19-e73a-43b5-b2b0-32a5640a764d.dat",
//            "d2ed7a73-5463-4b90-b226-80dd5d3f4a49.dat",
//            "90ec114a-09a9-4808-a99b-307b9590e8f5.dat"
    };

    static final class TagNode {
        final TagNode parent;
        final LinkedList<TagNode> children;
        final Tag<?> me;
        final String myName;
//        private static int distanceFromHead = -1;

        public TagNode(TagNode parent, Tag<?> me, String name) {
            this.parent = parent;
            this.me = me;
            this.myName = name;
            LinkedList<TagNode> addAllToChildren = new LinkedList<>();
//            System.out.println(me.getType());
//            ++distanceFromHead;
//            String numTabs = "";
//            for (int i=0;i<distanceFromHead;i++) numTabs+="\t";
//            System.out.println(numTabs+me.getType());
            if (me.getType()==TAG_LIST) {
                for (Tag<?> tag : ((ListTag<?>)me).getValue())
                    addAllToChildren .add(new TagNode(this, tag, tag.getName()));
            } else if (me.getType()==TAG_COMPOUND) {
                for (Map.Entry<String, Tag<?>> entry : ((CompoundTag)me).getValue().entrySet())
                    addAllToChildren .add(new TagNode(this, entry.getValue(), entry.getKey()));
            }
//            --distanceFromHead;
            if (addAllToChildren.size()>0) {
                this.children = new LinkedList<>();
                this.children.addAll(addAllToChildren);
            } else this.children = null;
        }

//        @Override
//        public boolean equals(Object obj) {
//            TagNode other;
//            try {
//                other = (TagNode)obj;
//            } catch (ClassCastException e) {return false;}
//            //Yeah, its ugly... get over it
//            if (me.getType()==other.me.getType()) {
//                if (me.getName()==other.me.getName()) {
//                    switch(other.me.getType()) {
//                        case TAG_LIST: {
//                            List<Tag<?>> tags = ((ListTag<Tag<?>>)me).getValue(),
//                                    otherTags = ((ListTag<Tag<?>>)other.me).getValue();
//                            return tags.equals(otherTags);
//                        }
//                        case TAG_COMPOUND: {
//
//                        }
//                        default:
//                            return me.getValue()==other.me.getValue();
//                    }
//                }
//            }
//        }
    }

    /**For performing recursive transformations onto input such that the finalized form of input will be returned
     * by the function {@link MonoPredicate#recurse(Object, MonoPredicate)}. Exit condition: <code>return null;</code>
     *
     * Gives the illusion of recursion to the {@link MonoPredicate#predicate(Object)} function during execution, while
     * the underlying logic utilizes a simple do-while loop to avoid blowing the stack on recursive operations with a
     * large number of frames.
     *
     * TODO maybe come up with a less abstract name?
     *
     * @param <IO> type restriction for the input
     */
    @FunctionalInterface
    interface MonoPredicate<IO> {
        static <I> I sRecurse(final I input, final MonoPredicate<I> func) {
            return func.recurse(input, func);
        }

        default IO recurse(IO input, MonoPredicate<IO> func) {
            IO prevFrame;
            do {
                prevFrame = input;
                input = func.predicate(input);
            } while(input != null);
            return prevFrame;
        }

        IO predicate(IO input);
    }

    static final class TagNodeProcessor extends Thread {
        final TagNode head;
        final MonoPredicate<TagNode> operation;
        protected volatile TagNode resultHead;

        public TagNodeProcessor(TagNode head, MonoPredicate<TagNode> operation) {
            super("TagNodeProcessor");
            this.head = head;
            this.operation = operation;
        }

        @Override
        public void run() {
            resultHead = operation.recurse(head, operation);
            interrupt();
        }

        //Not useful atm
//        public TagNode getResultHead() { return this.resultHead; }
    }

    //For lambda blocks
    static class ObjectWrapper<T> {
        public T t;
//        public ObjectWrapper(T obj) { this.t = obj; }
    }

    public static void main(String[] args) throws Exception {
        //Needs to be final to be accessible in lambda blocks
        final HashMap<String, ListTag<?>> inventories = getInventoryLists();
        final ItemMapContainer
                oldMap = new ItemMapContainer(oldCSVDir)
                , newMap = new ItemMapContainer(newDataDir);

        //Read in all CSV files and create mod-name-based hashmaps for easy unlocalized conversion
//        long start = System.currentTimeMillis();
        oldMap.assembleItemMap();
//        newMap.assembleItemMap();
        System.out.println("Old map size: " + oldMap.itemMap.size());
//        System.out.println("New map size: " + newMap.itemMap.size());
//        System.out.println("Map initialization took: " + (System.currentTimeMillis()-start));
//        for (String mod : oldMap.itemMap.keySet()) {
//            System.out.println("Mod: "+mod+" :: Entries: " + oldMap.itemMap.get(mod).size());
//        }

        //Convert the inventory tags into an easily iterable, linked, structure of nodes
        List<TagNode> nodes = new ArrayList<>();
//        long rstart = System.currentTimeMillis();
        for (Map.Entry<String, ListTag<?>> entry : inventories.entrySet())
            nodes.add(new TagNode(null, entry.getValue(), entry.getKey()));
//        System.out.println("Node generation took: "+(System.currentTimeMillis()-rstart));

        //Create backups of the original NBT files and remove the inventory LIST_TAG from the original file
        Date date = new Date();
        String backupSuffix = "-backup--"+date.getMonth()+"-"+date.getDay()+"-"+date.getYear()+"--"+
                date.getHours()+":"+date.getMinutes()+":"+date.getSeconds()+".dat";
        //Incase you need to read data from the original NBT structure -> maps original file name to backup file name
//        Map<String, String> originalBackupMap =
                createBackupsAndClearOrigInv(backupSuffix, oldDataDir, newDataDir);
//        originalBackupMap.forEach((k,v) -> System.out.println(k+" : "+v));

        //Convert the original short item ids to the new short id mappings
//        new ArrayList<TagNodeProcessor>() {
//            {
////                final ArrayList<TagNodeProcessor> listRef = this;
//                nodes.forEach((final TagNode headNode) -> {
//                    //TODO Shouldn't be accessed by multiple threads, but test to make sure
//                    final ObjectWrapper<TagNode> lastParent = new ObjectWrapper<>(), lastTag = new ObjectWrapper<>();
//                    add(new TagNodeProcessor(headNode, input -> {
//                        lastParent.t = input.parent;
//                        if (input.children.size()==0) {
//                            lastTag.t = new TagNode(lastParent.t, input.me, input.me.getName());
//                            if (input.me.getType()==TAG_SHORT&&input.me.getName().contentEquals("id")) {
//                                short newId = remapId(oldMap, newMap, ((ShortTag)input.me).getValue());
//                                return new TagNode(lastParent.t, new ShortTag("id", newId), "id");
//                            } else return lastParent.t;
//                        } else {
//                            if (input.me.getType()==TAG_LIST) {
//                                if () {
//
//                                }
//                            } else if (input.me.getType()==TAG_COMPOUND) {
//
//                            }
//                        }
//                    }));
//                });
//            }
//        };

//        for (Map.Entry<String, ListTag<?>> entry : inventories.entrySet()) {
//            System.out.println("Converting IDs in: '"+entry.getKey()+"' to unlocalized strings...");
//
//            System.out.println("Done!");
//        }
    }

    //TODO Test
    public static short remapId(ItemMapContainer oldMapping, ItemMapContainer newMapping, short id) {
        short toReturn = id;
        String modName = "";
        String unlocalizedName = "";
        Outer: for (String key : oldMapping.itemMap.keySet()) {
            TreeSet<ItemContainer> items = oldMapping.itemMap.get(key);
            for (ItemContainer item : items) {
                if (item.id == id) {
                    modName = key;
                    unlocalizedName = item.unlocalizedName;
                    break Outer;
                }
            }
        }
        if (!modName.contentEquals("") && !unlocalizedName.contentEquals("")) {
            for (ItemContainer item : newMapping.itemMap.get(modName)) {
                if (item.unlocalizedName.contentEquals(unlocalizedName)) {
                    toReturn = item.id;
                    break;
                }
            }
        }
        return toReturn;
    }

    //TODO Add backup equality checks, to avoid making redundant backups
    //TODO maybe come up with a nicer name?
    public static LinkedHashMap<String, String>
    createBackupsAndClearOrigInv(String backupSuffix, String oldDataDir, String newDataDir) throws IOException {
        return new LinkedHashMap<String, String>() {
            {
                for (String playerFile : playerFiles) {
                    File originalPlayerFile = new File(playerFile);
                    NBTInputStream nis = new NBTInputStream(new FileInputStream(oldDataDir + originalPlayerFile));
                    final CompoundTag original = (CompoundTag) nis.readTag();
                    nis.close();
                    final CompoundMap mapToWrite = new CompoundMap();
                    for (Map.Entry<String, Tag<?>> entry : original.getValue().entrySet())
                        if (!entry.getKey().equalsIgnoreCase("inventory"))
                            mapToWrite.put(entry.getKey(), entry.getValue());
                    final CompoundTag toWrite = new CompoundTag(original.getName(), mapToWrite);
                    String newFilePath = "", oldFilePath = oldDataDir+playerFile;
                    //If the old and new NBT dirs are the same, create backups
                    if (oldDataDir.contentEquals(newDataDir)) {
                        final String backupFileName =
                                playerFile.substring(0, playerFile.lastIndexOf(".dat")) + backupSuffix;
                        put(oldFilePath, oldDataDir+backupFileName);
                        System.out.println("Backing up '"+playerFile+"' to '"+backupFileName+"'");
                        File moveTo = new File(backupFileName);
                        moveTo.createNewFile();
                        //Make complete backup
                        NBTOutputStream nos = new NBTOutputStream(new FileOutputStream(oldDataDir + backupFileName));
                        nos.writeTag(original);
                        nos.close();
                        //Clear original file
                        originalPlayerFile.delete();
                        originalPlayerFile.createNewFile();
                        newFilePath = oldFilePath;
                    } else {
                        newFilePath = newDataDir + playerFile;
                        File newFile = new File(newFilePath);
                        if (newFile.exists()) {
                            System.err.println("WARNING: file '"+newFilePath+"' already exists, overwriting!");
                            newFile.delete();
                        }
                        newFile.createNewFile();
                        put(oldFilePath, newFilePath);
                    }
                    //Write the filtered NBT to the new file
                    NBTOutputStream nos = new NBTOutputStream(new FileOutputStream(newFilePath));
                    nos.writeTag(toWrite);
                    nos.close();
                }
            }
        };
    }

    public static HashMap<String, ListTag<?>> getInventoryLists() throws IOException {
        return new HashMap<String, ListTag<?>>() {
            {
                for (String playerFile : playerFiles) {
                    File playerData = new File(oldDataDir+playerFile);
                    if (!playerData.exists()) {
                        System.err.println("ERROR: Playerfile '" + playerFile + "' could not be found!");
                        continue;
                    }
                    NBTInputStream NBTis = new NBTInputStream(new FileInputStream(playerData));
                    CompoundTag test = (CompoundTag) NBTis.readTag();
                    for (Map.Entry<String, Tag<?>> entry : test.getValue().entrySet()) {
                        if (entry.getKey().equalsIgnoreCase("inventory")) {
                            put(playerFile, (ListTag<?>) entry.getValue());
                            break;
                        } else continue;
                    }
                    NBTis.close();
                }
            }
        };
    }
}
