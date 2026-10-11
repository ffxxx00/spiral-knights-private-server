package server;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import com.threerings.config.ConfigReference;
import com.threerings.presents.dobj.DSet;
import com.threerings.projectx.data.PlayerObject;
import com.threerings.projectx.item.config.ItemConfig;
import com.threerings.projectx.item.data.Item;
import com.threerings.projectx.item.data.LevelItem;
import com.threerings.config.ManagedConfig;
import java.util.TreeSet;

public class ItemCatalog {
    private static Field itemIdField;
    private static Field levelField;

    static {
        try {
            // Current live client mapping: VA() reads the frozen client per-instance id from bvX.
            itemIdField = Item.class.getDeclaredField("bvX");
            itemIdField.setAccessible(true);
            levelField = LevelItem.class.getDeclaredField("_level");
            levelField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static LevelItem createItem(long id, String configPath, int level, InstalledConfigManager manager) {
        try {
            ItemConfig config=manager.d(ItemConfig.class,configPath);
            if(config==null)throw new IllegalArgumentException("Missing frozen item "+configPath);
            // The constructor takes the catalog config ID, not the instance ID.
            LevelItem item = new LevelItem(config.getConfigId(), new ConfigReference<ItemConfig>(configPath));
            if (itemIdField != null) {
                itemIdField.set(item, Long.valueOf(id));
            }
            if (levelField != null) {
                levelField.set(item, Integer.valueOf(level));
            }
            return item;
        } catch (Exception e) {
            System.err.println("Error creating item: " + configPath + " -> " + e);
            return null;
        }
    }

    public static void populateKnightInventory(PlayerObject po, SceneManager scene) {
        InstalledConfigManager manager=scene.getConfigManager();
        List<Item> allItems = new ArrayList<>();
        long currentId = 1000L;

        // 1. Equipped Items:
        // Frozen ItemCodes.Group: HELM=0, ARMOR=1, SHIELD=2.
        LevelItem armor = createItem(currentId++, "Gear/Armor/Chaos Cloak", 10, manager);
        allItems.add(armor);
        po.equipment[1] = armor.VA();

        // Slot 0: Helm
        LevelItem helm = createItem(currentId++, "Gear/Helm/Chaos Cowl", 10, manager);
        allItems.add(helm);
        po.equipment[0] = helm.VA();

        // Slot 2: Shield
        LevelItem shield = createItem(currentId++, "Gear/Shield/Grey Owlite Shield", 10, manager);
        allItems.add(shield);
        po.equipment[2] = shield.VA();

        // Slot 6: Weapon 1 (Leviathan Blade)
        LevelItem w1 = createItem(currentId++, "Weapon/Sword/Leviathan Blade", 10, manager);
        allItems.add(w1);
        po.equipment[6] = w1.VA();

        // Slot 7: Weapon 2 (Combuster)
        LevelItem w2 = createItem(currentId++, "Weapon/Sword/Combuster", 10, manager);
        allItems.add(w2);
        po.equipment[7] = w2.VA();

        // Slot 8: Weapon 3 (Polaris)
        LevelItem w3 = createItem(currentId++, "Weapon/Handgun/Polaris", 10, manager);
        allItems.add(w3);
        po.equipment[8] = w3.VA();

        // Slot 9: Weapon 4 (Ash of Agni)
        LevelItem w4 = createItem(currentId++, "Weapon/Bomb/Ash of Agni", 10, manager);
        allItems.add(w4);
        po.equipment[9] = w4.VA();

        // 2. Extra Arsenal in Bag (Swords, Handguns, Bombs, Armor):
        String[] extraArsenal = new String[]{
            // Swords
            "Weapon/Sword/Glacius",
            "Weapon/Sword/Gran Faust",
            "Weapon/Sword/Divine Avenger",
            "Weapon/Sword/Fang of Vog",
            // Guns
            "Weapon/Handgun/Supernova",
            "Weapon/Handgun/Blitz Needle",
            "Weapon/Handgun/Argent Peacemaker",
            "Weapon/Handgun/Sentenza",
            // Bombs
            "Weapon/Bomb/Nitronome",
            "Weapon/Bomb/Shivermist Buster",
            "Weapon/Bomb/Dark Briar Barrage",
            // Armor / Shields
            "Gear/Armor/Dragon Scale Mail",
            "Gear/Shield/Dragon Scale Shield"
        };

        TreeSet<String> arsenal = new TreeSet<>(scene.getWeaponCatalog());
        // Preserve the existing extra armor/shield and never duplicate weapons.
        java.util.Collections.addAll(arsenal, extraArsenal);
        for (Item existing : allItems) arsenal.remove(existing.Mv().getName());
        for (String path : arsenal) {
            LevelItem extra = createItem(currentId++, path, 10, manager);
            if (extra != null) {
                allItems.add(extra);
            }
        }

        po.items = new DSet<>(allItems);
        System.out.println("[ItemCatalog] Populated " + allItems.size() + " test weapons & equipment items.");
    }

    public static List<String> discoverWeapons(InstalledConfigManager manager) {
        TreeSet<String> names=new TreeSet<>();
        for(ManagedConfig entry:manager.bd(ItemConfig.class).kL()) {
            // Resolve only weapon entries; resolving unrelated quest items can
            // initialize thousands of server-only actions during authentication.
            if(!entry.getName().startsWith("Weapon/"))continue;
            ItemConfig config=manager.d(ItemConfig.class,entry.getName());
            if(config==null || !config.creatable)continue;
            ItemConfig.Original item=config.w(manager);
            if(item instanceof ItemConfig.Sword || item instanceof ItemConfig.Handgun
                    || item instanceof ItemConfig.Bomb)names.add(entry.getName());
        }
        return java.util.Collections.unmodifiableList(new ArrayList<>(names));
    }
}
