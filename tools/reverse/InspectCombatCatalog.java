package server;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.threerings.config.*;
import com.threerings.projectx.item.config.ItemConfig;
import com.threerings.projectx.dungeon.config.*;
import com.threerings.tudey.config.*;

/** Read-only reachability audit of imported weapon catalogs; never a simulation. */
public final class InspectCombatCatalog {
    private final InstalledConfigManager configs = new InstalledConfigManager();
    private final Map<String,Class<? extends ManagedConfig>> groups = new LinkedHashMap<>();
    private final Map<String,Set<String>> consumers = new TreeMap<>();
    private final List<Object> weapons = new ArrayList<>();
    private final Map<String,Integer> groupSizes = new TreeMap<>();
    private final Map<String,String> inputHashes = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Expected local report path");
        InspectCombatCatalog audit=new InspectCombatCatalog();
        audit.run();
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("schema",1); report.put("version","20261006114613");
        report.put("scope","imported configuration reachability, not runtime parity");
        report.put("importer","server.InstalledConfigManager (includes local container restoration)");
        report.put("groups",audit.groupSizes); report.put("weapons",audit.weapons);
        report.put("input_sha256",audit.inputHashes);
        Map<String,Integer> counts=new TreeMap<>();
        audit.consumers.forEach((type,names)->counts.put(type,names.size()));
        report.put("weapons_by_type",counts);
        Files.writeString(Path.of(args[0]),json(report)+"\n",StandardCharsets.UTF_8);
        System.out.println("[AUDIT] weapons="+audit.weapons.size()+" reachableTypes="+counts.size()+" output="+args[0]);
    }

    private void run() throws Exception {
        groups.put("actor",ActorConfig.class); groups.put("item",ItemConfig.class);
        groups.put("attack",AttackConfig.class); groups.put("fire_action",FireActionConfig.class);
        groups.put("depth_scale",DepthScaleConfig.class);
        groups.put("level_table",com.threerings.projectx.item.config.LevelTableConfig.class);
        groups.put("item_property",com.threerings.projectx.item.config.ItemPropertyConfig.class);
        groups.put("status_condition",StatusConditionConfig.class);
        groups.put("handler",ParameterizedHandlerConfig.class);
        for(var group:groups.entrySet()) {
            Path path=Path.of("rsrc/config/"+group.getKey()+".dat");
            if(Files.exists(path)) {
                byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
                inputHashes.put(path.toString().replace('\\','/'),HexFormat.of().formatHex(digest));
                groupSizes.put(group.getKey(),configs.loadGroup(group.getValue(),path.toString()));
            }
        }
        for(String name:ItemCatalog.discoverWeapons(configs)) {
            ItemConfig.Weapon weapon=(ItemConfig.Weapon)configs.d(ItemConfig.class,name).w(configs);
            Scan scan=new Scan(name);
            for(int i=0;i<weapon.attacks.length;i++) scan.reference(AttackConfig.class,weapon.attacks[i].ref,"normal["+i+"]",0);
            scan.reference(AttackConfig.class,weapon.chargedAttack,"charged",0);
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("name",name); row.put("kind",weapon.getClass().getSimpleName());
            row.put("normal_attacks",weapon.attacks.length); row.put("charged",weapon.chargedAttack!=null);
            row.put("types",new ArrayList<>(scan.types)); row.put("references",scan.refs);
            row.put("details",scan.details); row.put("issues",scan.issues);
            row.put("incomplete_nodes",scan.incomplete);
            weapons.add(row);
            for(String type:scan.types) consumers.computeIfAbsent(type,ignored->new TreeSet<>()).add(name);
        }
    }

    private final class Scan {
        final String weapon;
        final Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<Class<?>,Set<ConfigReference<?>>> references=new HashMap<>();
        final Set<String> types=new TreeSet<>();
        final List<Object> refs=new ArrayList<>(),details=new ArrayList<>();
        final Set<String> issues=new TreeSet<>();
        final Set<String> incomplete=new TreeSet<>();
        Scan(String weapon) {this.weapon=weapon;}

        <T extends ManagedConfig> void reference(Class<T> type,ConfigReference<?> reference,String path,int depth) {
            if(reference==null) return;
            if(!references.computeIfAbsent(type,ignored->new HashSet<>()).add(reference)) return;
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("path",path); row.put("type",type.getName()); row.put("name",reference.getName());
            refs.add(row);
            try {
                @SuppressWarnings("unchecked") T config=configs.a(type,(ConfigReference<T>)reference);
                if(config==null) {issues.add("unresolved "+path+" -> "+type.getSimpleName()+":"+reference.getName());return;}
                Object resolved=config;
                if(config instanceof ActorConfig) resolved=((ActorConfig)config).ai(configs);
                else if(config instanceof AttackConfig) resolved=((AttackConfig)config).z(configs);
                else if(config instanceof ItemConfig) resolved=((ItemConfig)config).w(configs);
                walk(resolved,path+" -> "+reference.getName(),depth+1);
            } catch(RuntimeException error) {
                issues.add("resolve error "+path+": "+error.getClass().getSimpleName()+": "+error.getMessage());
            }
        }

        void walk(Object object,String path,int depth) {
            if(object==null) return;
            if(depth>64) {issues.add("depth limit "+path);return;}
            Class<?> type=object.getClass();
            if(type.isArray()) {
                for(int i=0;i<Array.getLength(object);i++) walk(Array.get(object,i),path+"["+i+"]",depth+1);
                return;
            }
            if(!(type.getName().startsWith("com.threerings.tudey.config.")
                    || type.getName().startsWith("com.threerings.projectx.dungeon.config."))) return;
            if(!seen.add(object)) return;
            types.add(type.getName());
            if(object instanceof ActionConfig.SpawnActor && ((ActionConfig.SpawnActor)object).actor==null)
                incomplete.add("null spawn reference: "+path);
            if(object instanceof ConditionConfig.Tagged && ((ConditionConfig.Tagged)object).tag.isEmpty())
                incomplete.add("empty tag condition: "+path);
            Map<String,Object> detail=new LinkedHashMap<>();
            detail.put("path",path); detail.put("type",type.getName());
            Field[] fields=type.getFields();
            Arrays.sort(fields,Comparator.comparing(Field::getName));
            for(Field field:fields) {
                if(Modifier.isStatic(field.getModifiers())) continue;
                try {
                    Object value=field.get(object);
                    if(value==null && ConfigReference.class.isAssignableFrom(field.getType()))
                        detail.put(field.getName(),null);
                    if(value instanceof ConfigReference) {
                        Type generic=field.getGenericType();
                        if(generic instanceof ParameterizedType) {
                            Type argument=((ParameterizedType)generic).getActualTypeArguments()[0];
                            if(argument instanceof Class && ManagedConfig.class.isAssignableFrom((Class<?>)argument)) {
                                @SuppressWarnings("unchecked") Class<? extends ManagedConfig> target=(Class<? extends ManagedConfig>)argument;
                                // Media/behavior/scale references are outside this action graph.
                                if(target==ActorConfig.class || target==AttackConfig.class || target==FireActionConfig.class
                                        || target==StatusConditionConfig.class || target==ParameterizedHandlerConfig.class)
                                    reference(target,(ConfigReference<?>)value,path+"."+field.getName(),depth+1);
                            } else issues.add("untyped reference "+path+"."+field.getName());
                        } else issues.add("untyped reference "+path+"."+field.getName());
                    } else if(value instanceof Number || value instanceof Boolean || value instanceof String || value instanceof Enum) {
                        detail.put(field.getName(),value instanceof Enum?value.toString():value);
                    } else walk(value,path+"."+field.getName(),depth+1);
                } catch(IllegalAccessException error) {throw new IllegalStateException(error);}
            }
            details.add(detail);
        }
    }

    private static String json(Object value) {
        if(value==null) return "null";
        if(value instanceof Boolean || value instanceof Number) return value.toString();
        if(value instanceof Map) {
            List<String> entries=new ArrayList<>();
            ((Map<?,?>)value).forEach((key,entry)->entries.add(json(key.toString())+":"+json(entry)));
            return "{"+String.join(",",entries)+"}";
        }
        if(value instanceof Iterable) {
            List<String> entries=new ArrayList<>();
            for(Object entry:(Iterable<?>)value) entries.add(json(entry));
            return "["+String.join(",",entries)+"]";
        }
        String text=value.toString(); StringBuilder escaped=new StringBuilder("\"");
        for(int i=0;i<text.length();i++) {
            char ch=text.charAt(i);
            if(ch=='"' || ch=='\\') escaped.append('\\').append(ch);
            else if(ch<32) escaped.append(String.format("\\u%04x",(int)ch));
            else escaped.append(ch);
        }
        return escaped.append('"').toString();
    }
}
