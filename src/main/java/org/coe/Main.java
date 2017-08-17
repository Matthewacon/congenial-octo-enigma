package org.coe;

import com.flowpowered.nbt.*;
import com.flowpowered.nbt.stream.NBTInputStream;
import com.flowpowered.nbt.stream.NBTOutputStream;
import joptsimple.OptionParser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static com.flowpowered.nbt.TagType.*;

final class RuntimeException extends java.lang.RuntimeException {
    public RuntimeException setCause(Exception e) {
        initCause(e);
        return this;
    }
}

final class ItemContainer implements Comparable<ItemContainer> {
    final String unlocalizedName;
    final String itemName;
    final short id;
    public ItemContainer(String unlocalizedName, String itemName, short id) {
        this.unlocalizedName = unlocalizedName;
        this.itemName = itemName;
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
            System.out.println("Parsing CSV...");
            List<CSVRecord> records;
            try {
                records = parser.getRecords();
            } catch (IOException e) { throw new RuntimeException().setCause(e); }
            int recordCount = records.size();
            final float chunkSize = 4.0f;
            final double blkSize = Math.floor(recordCount / chunkSize);
            //Skip header
//            for (CSVRecord record : records) {
//                if (record.get("Name").contentEquals("Name")) continue;
//                String modName = record.get("Name"), itemName = record.get("Class");
//                modName = modName.substring(0, modName.lastIndexOf(":"));
//                ItemContainer container = new ItemContainer(itemName, Short.parseShort(record.get("ID")));
//                if (itemMap.containsKey(modName)) {
//                    itemMap.get(modName).add(container);
//                } else {
//                    TreeSet<ItemContainer> set = new TreeSet<>();
//                    set.add(container);
//                    itemMap.put(modName, set);
//                }
//            }
            for (int i=1; i<recordCount; i++) {
                CSVRecord record = records.get(i);
                String
                        modName = record.get("Name"),
                        unlocalizedName = record.get("Class"),
                        itemName = modName.substring(modName.lastIndexOf(":")+1, modName.length());
//                if (modName.contains(":"))
                modName = modName.substring(0, modName.lastIndexOf(":"));
                ItemContainer container = new ItemContainer(unlocalizedName, itemName, Short.parseShort(record.get("ID")));
                if (itemMap.keySet().contains(modName)) {
//                    System.out.println("Existing mod entry found: '"+ modName +"'");
                    itemMap.get(modName).add(container);
                } else {
//                    System.out.println("Adding new mod entry: '" + modName + "'");
                    TreeSet<ItemContainer> set = new TreeSet<>();
                    set.add(container);
                    itemMap.put(modName, set);
                }
                if ((i%blkSize)==0) {
                    String fileName = csvDir.substring(csvDir.lastIndexOf(Main.PWD)+1, csvDir.length());
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
        System.out.println("Constructing ItemMapContainer: '"+csvDir+"'");
        for (String dir : new String[] {csvDir, csvDir+"block.csv", csvDir+"item.csv"}) {
            if (!new File(dir).exists()) throw new IOException(INVALID_DIR.replace("DIR", dir));
        }
        this.csvFilePath = csvDir;
    }

    public void assembleItemMap() throws IOException {
        CSVParserThread[] waitOnThese = new CSVParserThread[] {
                new CSVParserThread().init(csvFilePath+"item.csv", ITEM_HEADERS_AND_TYPES),
//                new CSVParserThread().init(csvFilePath+"block.csv", BLOCK_HEADERS_AND_TYPES)
        };
        Outer: while (true) {
//            try {
//                Thread.currentThread().sleep(1000);
//            } catch (InterruptedException e) {
//                throw new RuntimeException().setCause(e);
//            }
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
//            "PAST PLAYER FILES HERE, 1 FILE PER ENTRY"
    };

    static final class TagNode {
        final TagNode parent;
        final LinkedList<TagNode> children;
        private Tag<?> me;
        final String myName;
        final Function<? super Tag<?>, Tag<?>> tagOperator;

        private static final Function<?super Tag<?>, Tag<?>> DEFAULT = (input) -> input;

//        private static int distanceFromHead = -1;

        public TagNode(TagNode parent, Tag<?> me, String name) {
            this(
                parent,
                me,
                name,
                //Use default function is none specified (just return the original tag)
                parent==null?DEFAULT:(parent.tagOperator==null?DEFAULT:parent.tagOperator)
            );
        }

        public TagNode(TagNode parent, Tag<?> me, String name, Function<?super Tag<?>, Tag<?>> tagOperator) {
            this.parent = parent;
            this.me = parent==null?tagOperator.apply(me):me;
            this.myName = name;
            this.tagOperator = tagOperator;
            List<TagNode> addAllToChildren = new LinkedList<>();
//            System.out.println(me.getType());
//            ++distanceFromHead;
//            String numTabs = "";
//            for (int i=0;i<distanceFromHead;i++) numTabs+="\t";
//            System.out.println(numTabs+me.getType());
            if (me.getType() == TAG_LIST) {
                for (Tag<?> tag : ((ListTag<?>)me).getValue()) {
                    tag = tagOperator.apply(tag);
                    addAllToChildren.add(new TagNode(this, tag, tag.getName()));
                }
                ListTag<?> newTag = new ListTag(me.getName(), ((ListTag)me).getElementType(), new LinkedList() {
                    { for (TagNode node : addAllToChildren) add(node.me); }
                });
                this.me=newTag;
            } else if (me.getType() == TAG_COMPOUND) {
                for (Map.Entry<String, Tag<?>> entry : ((CompoundTag)me).getValue().entrySet()) {
                    Tag<?> tag = tagOperator.apply(entry.getValue());
                    addAllToChildren.add(new TagNode(this, tag, tag.getName()));
                }
                CompoundMap map = ((CompoundTag)me).getValue();
                map.clear();
                for (TagNode node : addAllToChildren) map.put(node.me.getName(), node.me);
            }
//            --distanceFromHead;
            if (addAllToChildren.size()>0) {
                this.children = new LinkedList<>();
                this.children.addAll(addAllToChildren);

            } else this.children = null;
        }

        public Tag<?> getMe() {
            return this.me;
        }
    }

    public static void main(String[] args) throws Exception {
        HashMap<String, ListTag<?>> inventories = getInventoryLists();
        ItemMapContainer oldMap = new ItemMapContainer(oldCSVDir), newMap = new ItemMapContainer(newCSVDir);

        //Read in all CSV files and create mod-name-based hashmaps for easy unlocalized conversion
        long start = System.currentTimeMillis();
        oldMap.assembleItemMap();
        newMap.assembleItemMap();
        System.out.println("Old map size: " + oldMap.itemMap.size());
        System.out.println("New map size: " + newMap.itemMap.size());
        System.out.println("Map initialization took: " + (System.currentTimeMillis()-start) + "ms");

        //Convert the inventory tags into an easily iterable, linked, structure of nodes
        HashMap<String, TagNode> nodes = new HashMap<>();
        long rstart = System.currentTimeMillis();
        for (Map.Entry<String, ListTag<?>> entry : inventories.entrySet())
            nodes.put(
                entry.getKey(),
                new TagNode(
                null,
                    entry.getValue(),
                    entry.getKey(),
                    (input) -> input.getType()==TAG_SHORT&&input.getName().contentEquals("id") ?
                            new ShortTag(input.getName(), remapId(oldMap, newMap, ((ShortTag)input).getValue())):input
                )
            );
        System.out.println("Node generation took: "+(System.currentTimeMillis()-rstart) + "ms");

        //Create backups of the original NBT files and remove the inventory LIST_TAG from the original file
        Date date = new Date();
        String backupSuffix = "-backup--"+date.getMonth()+"-"+date.getDay()+"-"+date.getYear()+"--"+
                date.getHours()+":"+date.getMinutes()+":"+date.getSeconds()+".dat";
        //In case you need to read data from the original NBT structure -> maps original file name to backup file name
        Map<String, String> originalBackupMap = createBackupsAndClearOrigInv(backupSuffix, oldDataDir, newDataDir);

        //Write the new inventory list to the original player file along with the rest of the NBT
        for (Map.Entry<String, String> backupEntry : originalBackupMap.entrySet()) {
            String origFileName = backupEntry.getKey();
            origFileName = origFileName.substring(origFileName.lastIndexOf("/")+1, origFileName.length());
            TagNode writeMe = nodes.get(origFileName);
            NBTInputStream originalNis = new NBTInputStream(new FileInputStream(backupEntry.getKey()));
            CompoundTag originalTag = (CompoundTag)originalNis.readTag();
            originalNis.close();
            CompoundMap filteredMap = new CompoundMap() {
                {
                    final CompoundMap ref = this;
                    originalTag.getValue().values().forEach(tag -> {
                        if (!tag.getName().contentEquals("id")&&!(tag.getType()==TAG_SHORT))
                            ref.put(tag.getName(), tag);
                    });
                }
            };
            filteredMap.put("Inventory", writeMe.me);
            NBTOutputStream nos = new NBTOutputStream(new FileOutputStream(newDataDir+origFileName));
            nos.writeTag(new CompoundTag("", filteredMap));
            nos.close();
        }

//        for (Map.Entry<String, TagNode> entry : nodes.entrySet()) {
//            System.out.println(entry.getKey() + " :: " + entry.getValue());
//
//        }

//        for (int i=0;i<nodes.size(); i++) {
//            NBTOutputStream nos = new NBTOutputStream(new FileOutputStream(PWD+i+"test.dat"));
//            CompoundMap map = new CompoundMap();
//            map.put("Inventory", nodes.get(i).me);
//            nos.writeTag(new CompoundTag("", map));
//            nos.close();
//        }

        System.out.println("Number of items converted: " + numItems);
    }

    public static int numItems = 0;

    public static short remapId(ItemMapContainer oldMapping, ItemMapContainer newMapping, short id) {
        ++numItems;
        short toReturn = id;
        String modName = "", unlocalizedName = "", itemName = "";
        Outer: for (String key : oldMapping.itemMap.keySet()) {
            TreeSet<ItemContainer> items = oldMapping.itemMap.get(key);
            for (ItemContainer item : items) {
                if (item.id==id) {
                    modName = key;
                    unlocalizedName = item.unlocalizedName;
                    itemName = item.itemName;
                    break Outer;
                }
            }
        }
        if (!modName.contentEquals("") && !unlocalizedName.contentEquals("")) {
            TreeSet<ItemContainer> items = newMapping.itemMap.get(modName);
            if (!(items==null)) {
                for (ItemContainer item : items) {
                    if (item.unlocalizedName.contentEquals(unlocalizedName)&&item.itemName.contentEquals(itemName)) {
                        toReturn = item.id;
                        break;
                    }
                }
            } else {
                System.err.println("WARNING: Mod '"+modName+"' not found in new item map, id '"+id+"' for item " +
                        "'"+unlocalizedName+"' will remain unchanged!");
                return toReturn;
            }
        } else {
            System.err.println("WARNING: item id '"+id+"' cannot be found in the original item map! It will " +
                    "remain unchanged!");
            return toReturn;
        }
        if (toReturn==id) System.out.println("INFO: Item '"+modName+":"+"["+itemName+","+unlocalizedName+"]"+"' " +
                "with id "+id+", is the same in both maps.");
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
                    NBTInputStream nis = new NBTInputStream(new FileInputStream(oldDataDir + playerFile));
                    final CompoundTag original = (CompoundTag) nis.readTag();
                    nis.close();
                    final CompoundMap mapToWrite = new CompoundMap();
                    for (Map.Entry<String, Tag<?>> entry : original.getValue().entrySet())
                        if (!entry.getKey().equalsIgnoreCase("inventory"))
                            mapToWrite.put(entry.getKey(), entry.getValue());
//                    final CompoundTag toWrite = new CompoundTag(original.getName(), mapToWrite);
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
//                        originalPlayerFile.createNewFile();
//                        newFilePath = oldFilePath;
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
//                    //Write the filtered NBT to the new file
//                    NBTOutputStream nos = new NBTOutputStream(new FileOutputStream(newFilePath));
//                    nos.writeTag(toWrite);
//                    nos.close();
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
