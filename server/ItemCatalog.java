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

    public static LevelItem createItem(long id, String configPath, int level) {
        try {
            LevelItem item = new LevelItem((int)id, new ConfigReference<ItemConfig>(configPath));
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

    public static void populateKnightInventory(PlayerObject po) {
        List<Item> allItems = new ArrayList<>();
        long currentId = 1000L;

        // 1. Equipped Items:
        // Slot 0: Armor
        LevelItem armor = createItem(currentId++, "Gear/Armor/Chaos Cloak", 10);
        allItems.add(armor);
        po.equipment[0] = armor.VA();

        // Slot 1: Helm
        LevelItem helm = createItem(currentId++, "Gear/Helm/Chaos Cowl", 10);
        allItems.add(helm);
        po.equipment[1] = helm.VA();

        // Slot 2: Shield
        LevelItem shield = createItem(currentId++, "Gear/Shield/Grey Owlite Shield", 10);
        allItems.add(shield);
        po.equipment[2] = shield.VA();

        // Slot 6: Weapon 1 (Leviathan Blade)
        LevelItem w1 = createItem(currentId++, "Weapon/Sword/Leviathan Blade", 10);
        allItems.add(w1);
        po.equipment[6] = w1.VA();

        // Slot 7: Weapon 2 (Combuster)
        LevelItem w2 = createItem(currentId++, "Weapon/Sword/Combuster", 10);
        allItems.add(w2);
        po.equipment[7] = w2.VA();

        // Slot 8: Weapon 3 (Polaris)
        LevelItem w3 = createItem(currentId++, "Weapon/Handgun/Polaris", 10);
        allItems.add(w3);
        po.equipment[8] = w3.VA();

        // Slot 9: Weapon 4 (Ash of Agni)
        LevelItem w4 = createItem(currentId++, "Weapon/Bomb/Ash of Agni", 10);
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

        for (String path : extraArsenal) {
            LevelItem extra = createItem(currentId++, path, 10);
            if (extra != null) {
                allItems.add(extra);
            }
        }

        po.items = new DSet<>(allItems);
        System.out.println("[ItemCatalog] Populated " + allItems.size() + " test weapons & equipment items.");
    }
}
