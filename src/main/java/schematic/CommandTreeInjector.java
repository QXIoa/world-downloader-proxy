package schematic;

import config.Config;
import java.util.ArrayList;
import java.util.List;
import packets.DataTypeProvider;
import packets.builder.PacketBuilder;
import proxy.PacketInjector;

/**
 * Intercepts the server's {@code DeclareCommands} packet and injects our proxy command
 * ({@code /world-downloader-proxy}) into the command tree before forwarding it to the client.
 *
 * <p>Without this, the client doesn't know about our command and will:
 * <ul>
 *   <li>show "Unknown or incomplete command" when the user types it</li>
 *   <li>not send {@code CommandSuggestion} tab-completion requests for it</li>
 * </ul>
 *
 * <p>The DeclareCommands packet is a graph of command nodes. We parse it, add three new
 * literal nodes ({@code world-downloader-proxy}, {@code area-selection},
 * {@code schematic-export}), and add the first as a child of the root node.
 *
 * <p>Node flags layout (byte):
 * <pre>
 *   bits 0-1: node type (0=root, 1=literal, 2=argument)
 *   bit    2: isExecutable
 *   bit    3: hasRedirect
 *   bit    4: hasCustomSuggestions
 *   bits 5-7: unused
 * </pre>
 *
 * <p><b>Version compatibility:</b> Since Minecraft 1.19 (protocol 759), the parser field in
 * argument nodes changed from a string to a VarInt ID. The parser IDs themselves have shifted
 * across versions as new parsers were added and old ones removed. This class maintains
 * version-specific parser ID tables for all supported protocol versions:
 * <ul>
 *   <li>protocol &lt; 759 (1.13–1.18): parser sent as string</li>
 *   <li>protocol 759–760 (1.19–1.19.2): VarInt, 48 parsers</li>
 *   <li>protocol 761–762 (1.19.3–1.19.4): VarInt, 48 parsers (remapped)</li>
 *   <li>protocol 763–765 (1.20–1.20.4): VarInt, 49 parsers (added heightmap)</li>
 *   <li>protocol 766–769 (1.20.5–1.21.4): VarInt, 54 parsers (added style, item_slots, loot_*)</li>
 *   <li>protocol 770–771 (1.21.5–1.21.7): VarInt, 55 parsers (added resource_selector)</li>
 *   <li>protocol 772+ (1.21.8+): VarInt, 57 parsers (added hex_color, dialog)</li>
 * </ul>
 *
 * <p>Sources:
 * <ul>
 *   <li><a href="https://minecraft.wiki/w/Java_Edition_protocol/Command_data">Minecraft Wiki — Command data</a></li>
 *   <li><a href="https://github.com/PrismarineJS/minecraft-data">PrismarineJS minecraft-data</a> — protocol.json for each version</li>
 * </ul>
 */
public final class CommandTreeInjector {

    public static final String ROOT_COMMAND = "world-downloader-proxy";

    /** Subcommands registered in {@link SelectionCommandRouter}. */
    private static final String[] SUBCOMMANDS = {
        "area-selection",
        "schematic-export",
        "pos1",
        "pos2",
        "fly"
    };

    // Node type constants
    private static final int TYPE_ROOT = 0;
    private static final int TYPE_LITERAL = 1;
    private static final int TYPE_ARGUMENT = 2;

    // Flag bits
    private static final int FLAG_EXECUTABLE = 0x04;
    private static final int FLAG_HAS_REDIRECT = 0x08;
    private static final int FLAG_HAS_SUGGESTIONS = 0x10;

    // --- Protocol version boundaries for parser ID mappings ---
    private static final int PROTOCOL_1_19      = 759;  // parser changed from string to VarInt
    private static final int PROTOCOL_1_19_3    = 761;  // parser IDs remapped
    private static final int PROTOCOL_1_20      = 763;  // heightmap added; time gets properties
    private static final int PROTOCOL_1_20_5    = 766;  // style, item_slots, loot_* added
    private static final int PROTOCOL_1_21_5    = 770;  // resource_selector added
    private static final int PROTOCOL_1_21_8    = 772;  // hex_color, dialog added

    // --- Parser ID tables (indexed by VarInt ID, value = string identifier) ---
    // Source: PrismarineJS minecraft-data protocol.json for each version

    /** 1.19–1.19.2 (protocol 759–760): 48 parsers (0–47). */
    private static final String[] PARSERS_1_19 = {
        "brigadier:bool",            // 0
        "brigadier:float",           // 1
        "brigadier:double",          // 2
        "brigadier:integer",         // 3
        "brigadier:long",            // 4
        "brigadier:string",          // 5
        "minecraft:entity",          // 6
        "minecraft:game_profile",    // 7
        "minecraft:block_pos",       // 8
        "minecraft:column_pos",      // 9
        "minecraft:vec3",            // 10
        "minecraft:vec2",            // 11
        "minecraft:block_state",     // 12
        "minecraft:block_predicate", // 13
        "minecraft:item_stack",      // 14
        "minecraft:item_predicate",  // 15
        "minecraft:color",           // 16
        "minecraft:component",       // 17
        "minecraft:message",         // 18
        "minecraft:nbt",             // 19
        "minecraft:nbt_tag",         // 20
        "minecraft:nbt_path",        // 21
        "minecraft:objective",       // 22
        "minecraft:objective_criteria", // 23
        "minecraft:operation",       // 24
        "minecraft:particle",        // 25
        "minecraft:angle",           // 26
        "minecraft:rotation",        // 27
        "minecraft:scoreboard_slot", // 28
        "minecraft:score_holder",    // 29
        "minecraft:swizzle",         // 30
        "minecraft:team",            // 31
        "minecraft:item_slot",       // 32
        "minecraft:resource_location", // 33
        "minecraft:mob_effect",      // 34
        "minecraft:function",        // 35
        "minecraft:entity_anchor",   // 36
        "minecraft:int_range",       // 37
        "minecraft:float_range",     // 38
        "minecraft:item_enchantment",// 39
        "minecraft:entity_summon",   // 40
        "minecraft:dimension",       // 41
        "minecraft:time",            // 42
        "minecraft:resource_or_tag", // 43
        "minecraft:resource",        // 44
        "minecraft:template_mirror", // 45
        "minecraft:template_rotation", // 46
        "minecraft:uuid",            // 47
    };

    /** 1.19.3–1.19.4 (protocol 761–762): 48 parsers (0–47). Removed mob_effect, item_enchantment, entity_summon; added gamemode, resource_or_tag_key, resource_key. */
    private static final String[] PARSERS_1_19_3 = {
        "brigadier:bool",            // 0
        "brigadier:float",           // 1
        "brigadier:double",          // 2
        "brigadier:integer",         // 3
        "brigadier:long",            // 4
        "brigadier:string",          // 5
        "minecraft:entity",          // 6
        "minecraft:game_profile",    // 7
        "minecraft:block_pos",       // 8
        "minecraft:column_pos",      // 9
        "minecraft:vec3",            // 10
        "minecraft:vec2",            // 11
        "minecraft:block_state",     // 12
        "minecraft:block_predicate", // 13
        "minecraft:item_stack",      // 14
        "minecraft:item_predicate",  // 15
        "minecraft:color",           // 16
        "minecraft:component",       // 17
        "minecraft:message",         // 18
        "minecraft:nbt",             // 19
        "minecraft:nbt_tag",         // 20
        "minecraft:nbt_path",        // 21
        "minecraft:objective",       // 22
        "minecraft:objective_criteria", // 23
        "minecraft:operation",       // 24
        "minecraft:particle",        // 25
        "minecraft:angle",           // 26
        "minecraft:rotation",        // 27
        "minecraft:scoreboard_slot", // 28
        "minecraft:score_holder",    // 29
        "minecraft:swizzle",         // 30
        "minecraft:team",            // 31
        "minecraft:item_slot",       // 32
        "minecraft:resource_location", // 33
        "minecraft:function",        // 34
        "minecraft:entity_anchor",   // 35
        "minecraft:int_range",       // 36
        "minecraft:float_range",     // 37
        "minecraft:dimension",       // 38
        "minecraft:gamemode",        // 39
        "minecraft:time",            // 40
        "minecraft:resource_or_tag", // 41
        "minecraft:resource_or_tag_key", // 42
        "minecraft:resource",        // 43
        "minecraft:resource_key",    // 44
        "minecraft:template_mirror", // 45
        "minecraft:template_rotation", // 46
        "minecraft:uuid",            // 47
    };

    /** 1.20–1.20.4 (protocol 763–765): 49 parsers (0–48). Added heightmap. */
    private static final String[] PARSERS_1_20 = {
        "brigadier:bool",            // 0
        "brigadier:float",           // 1
        "brigadier:double",          // 2
        "brigadier:integer",         // 3
        "brigadier:long",            // 4
        "brigadier:string",          // 5
        "minecraft:entity",          // 6
        "minecraft:game_profile",    // 7
        "minecraft:block_pos",       // 8
        "minecraft:column_pos",      // 9
        "minecraft:vec3",            // 10
        "minecraft:vec2",            // 11
        "minecraft:block_state",     // 12
        "minecraft:block_predicate", // 13
        "minecraft:item_stack",      // 14
        "minecraft:item_predicate",  // 15
        "minecraft:color",           // 16
        "minecraft:component",       // 17
        "minecraft:message",         // 18
        "minecraft:nbt",             // 19
        "minecraft:nbt_tag",         // 20
        "minecraft:nbt_path",        // 21
        "minecraft:objective",       // 22
        "minecraft:objective_criteria", // 23
        "minecraft:operation",       // 24
        "minecraft:particle",        // 25
        "minecraft:angle",           // 26
        "minecraft:rotation",        // 27
        "minecraft:scoreboard_slot", // 28
        "minecraft:score_holder",    // 29
        "minecraft:swizzle",         // 30
        "minecraft:team",            // 31
        "minecraft:item_slot",       // 32
        "minecraft:resource_location", // 33
        "minecraft:function",        // 34
        "minecraft:entity_anchor",   // 35
        "minecraft:int_range",       // 36
        "minecraft:float_range",     // 37
        "minecraft:dimension",       // 38
        "minecraft:gamemode",        // 39
        "minecraft:time",            // 40
        "minecraft:resource_or_tag", // 41
        "minecraft:resource_or_tag_key", // 42
        "minecraft:resource",        // 43
        "minecraft:resource_key",    // 44
        "minecraft:template_mirror", // 45
        "minecraft:template_rotation", // 46
        "minecraft:heightmap",       // 47
        "minecraft:uuid",            // 48
    };

    /** 1.20.5–1.21.4 (protocol 766–769): 54 parsers (0–53). Added style, item_slots, loot_table, loot_predicate, loot_modifier. */
    private static final String[] PARSERS_1_20_5 = {
        "brigadier:bool",            // 0
        "brigadier:float",           // 1
        "brigadier:double",          // 2
        "brigadier:integer",         // 3
        "brigadier:long",            // 4
        "brigadier:string",          // 5
        "minecraft:entity",          // 6
        "minecraft:game_profile",    // 7
        "minecraft:block_pos",       // 8
        "minecraft:column_pos",      // 9
        "minecraft:vec3",            // 10
        "minecraft:vec2",            // 11
        "minecraft:block_state",     // 12
        "minecraft:block_predicate", // 13
        "minecraft:item_stack",      // 14
        "minecraft:item_predicate",  // 15
        "minecraft:color",           // 16
        "minecraft:component",       // 17
        "minecraft:style",           // 18
        "minecraft:message",         // 19
        "minecraft:nbt",             // 20
        "minecraft:nbt_tag",         // 21
        "minecraft:nbt_path",        // 22
        "minecraft:objective",       // 23
        "minecraft:objective_criteria", // 24
        "minecraft:operation",       // 25
        "minecraft:particle",        // 26
        "minecraft:angle",           // 27
        "minecraft:rotation",        // 28
        "minecraft:scoreboard_slot", // 29
        "minecraft:score_holder",    // 30
        "minecraft:swizzle",         // 31
        "minecraft:team",            // 32
        "minecraft:item_slot",       // 33
        "minecraft:item_slots",      // 34
        "minecraft:resource_location", // 35
        "minecraft:function",        // 36
        "minecraft:entity_anchor",   // 37
        "minecraft:int_range",       // 38
        "minecraft:float_range",     // 39
        "minecraft:dimension",       // 40
        "minecraft:gamemode",        // 41
        "minecraft:time",            // 42
        "minecraft:resource_or_tag", // 43
        "minecraft:resource_or_tag_key", // 44
        "minecraft:resource",        // 45
        "minecraft:resource_key",    // 46
        "minecraft:template_mirror", // 47
        "minecraft:template_rotation", // 48
        "minecraft:heightmap",       // 49
        "minecraft:loot_table",      // 50
        "minecraft:loot_predicate",  // 51
        "minecraft:loot_modifier",   // 52
        "minecraft:uuid",            // 53
    };

    /** 1.21.5–1.21.7 (protocol 770–771): 55 parsers (0–54). Added resource_selector. */
    private static final String[] PARSERS_1_21_5 = {
        "brigadier:bool",            // 0
        "brigadier:float",           // 1
        "brigadier:double",          // 2
        "brigadier:integer",         // 3
        "brigadier:long",            // 4
        "brigadier:string",          // 5
        "minecraft:entity",          // 6
        "minecraft:game_profile",    // 7
        "minecraft:block_pos",       // 8
        "minecraft:column_pos",      // 9
        "minecraft:vec3",            // 10
        "minecraft:vec2",            // 11
        "minecraft:block_state",     // 12
        "minecraft:block_predicate", // 13
        "minecraft:item_stack",      // 14
        "minecraft:item_predicate",  // 15
        "minecraft:color",           // 16
        "minecraft:component",       // 17
        "minecraft:style",           // 18
        "minecraft:message",         // 19
        "minecraft:nbt",             // 20
        "minecraft:nbt_tag",         // 21
        "minecraft:nbt_path",        // 22
        "minecraft:objective",       // 23
        "minecraft:objective_criteria", // 24
        "minecraft:operation",       // 25
        "minecraft:particle",        // 26
        "minecraft:angle",           // 27
        "minecraft:rotation",        // 28
        "minecraft:scoreboard_slot", // 29
        "minecraft:score_holder",    // 30
        "minecraft:swizzle",         // 31
        "minecraft:team",            // 32
        "minecraft:item_slot",       // 33
        "minecraft:item_slots",      // 34
        "minecraft:resource_location", // 35
        "minecraft:function",        // 36
        "minecraft:entity_anchor",   // 37
        "minecraft:int_range",       // 38
        "minecraft:float_range",     // 39
        "minecraft:dimension",       // 40
        "minecraft:gamemode",        // 41
        "minecraft:time",            // 42
        "minecraft:resource_or_tag", // 43
        "minecraft:resource_or_tag_key", // 44
        "minecraft:resource",        // 45
        "minecraft:resource_key",    // 46
        "minecraft:resource_selector", // 47
        "minecraft:template_mirror", // 48
        "minecraft:template_rotation", // 49
        "minecraft:heightmap",       // 50
        "minecraft:loot_table",      // 51
        "minecraft:loot_predicate",  // 52
        "minecraft:loot_modifier",   // 53
        "minecraft:uuid",            // 54
    };

    /** 1.21.8+ (protocol 772+): 57 parsers (0–56). Added hex_color, dialog. */
    private static final String[] PARSERS_1_21_8 = {
        "brigadier:bool",            // 0
        "brigadier:float",           // 1
        "brigadier:double",          // 2
        "brigadier:integer",         // 3
        "brigadier:long",            // 4
        "brigadier:string",          // 5
        "minecraft:entity",          // 6
        "minecraft:game_profile",    // 7
        "minecraft:block_pos",       // 8
        "minecraft:column_pos",      // 9
        "minecraft:vec3",            // 10
        "minecraft:vec2",            // 11
        "minecraft:block_state",     // 12
        "minecraft:block_predicate", // 13
        "minecraft:item_stack",      // 14
        "minecraft:item_predicate",  // 15
        "minecraft:color",           // 16
        "minecraft:hex_color",       // 17
        "minecraft:component",       // 18
        "minecraft:style",           // 19
        "minecraft:message",         // 20
        "minecraft:nbt",             // 21
        "minecraft:nbt_tag",         // 22
        "minecraft:nbt_path",        // 23
        "minecraft:objective",       // 24
        "minecraft:objective_criteria", // 25
        "minecraft:operation",       // 26
        "minecraft:particle",        // 27
        "minecraft:angle",           // 28
        "minecraft:rotation",        // 29
        "minecraft:scoreboard_slot", // 30
        "minecraft:score_holder",    // 31
        "minecraft:swizzle",         // 32
        "minecraft:team",            // 33
        "minecraft:item_slot",       // 34
        "minecraft:item_slots",      // 35
        "minecraft:resource_location", // 36
        "minecraft:function",        // 37
        "minecraft:entity_anchor",   // 38
        "minecraft:int_range",       // 39
        "minecraft:float_range",     // 40
        "minecraft:dimension",       // 41
        "minecraft:gamemode",        // 42
        "minecraft:time",            // 43
        "minecraft:resource_or_tag", // 44
        "minecraft:resource_or_tag_key", // 45
        "minecraft:resource",        // 46
        "minecraft:resource_key",    // 47
        "minecraft:resource_selector", // 48
        "minecraft:template_mirror", // 49
        "minecraft:template_rotation", // 50
        "minecraft:heightmap",       // 51
        "minecraft:loot_table",      // 52
        "minecraft:loot_predicate",  // 53
        "minecraft:loot_modifier",   // 54
        "minecraft:dialog",          // 55
        "minecraft:uuid",            // 56
    };

    /**
     * Select the correct parser ID table for the current protocol version.
     * @return the parser string array, or {@code null} if pre-1.19 (parser sent as string).
     */
    private static String[] getParserTable(int protocolVersion) {
        if (protocolVersion < PROTOCOL_1_19)      return null;  // pre-1.19: string-based
        if (protocolVersion < PROTOCOL_1_19_3)   return PARSERS_1_19;
        if (protocolVersion < PROTOCOL_1_20)     return PARSERS_1_19_3;
        if (protocolVersion < PROTOCOL_1_20_5)   return PARSERS_1_20;
        if (protocolVersion < PROTOCOL_1_21_5)   return PARSERS_1_20_5;
        if (protocolVersion < PROTOCOL_1_21_8)   return PARSERS_1_21_5;
        return PARSERS_1_21_8;
    }

    /** Parsed single node — stores raw bytes + parsed children for the root. */
    private static final class Node {
        final byte[] raw;
        final int flags;
        final int[] children;

        Node(byte[] raw, int flags, int[] children) {
            this.raw = raw;
            this.flags = flags;
            this.children = children;
        }

        int type() { return flags & 0x03; }
        boolean hasRedirect() { return (flags & FLAG_HAS_REDIRECT) != 0; }
    }

    /**
     * Process the DeclareCommands packet. The {@code provider} is positioned after the
     * packet ID VarInt has been read.
     *
     * @return {@code true} to forward the original packet unchanged,
     *         {@code false} if we injected a modified version.
     */
    public boolean process(DataTypeProvider provider) {
        PacketInjector injector = Config.getPacketInjector();
        if (injector == null) {
            return true; // can't inject, forward original
        }

        try {
            return processInternal(provider, injector);
        } catch (Exception e) {
            System.out.println("[CommandTreeInjector] Failed to modify command tree: " + e.getMessage());
            e.printStackTrace();
            return true; // forward original on error
        }
    }

    private boolean processInternal(DataTypeProvider provider, PacketInjector injector) {
        int protocolVersion = Config.getProtocolVersion();
        String[] parserTable = getParserTable(protocolVersion);
        boolean parserIsVarInt = parserTable != null;

        // Read count
        int count = provider.readVarInt();

        // Parse all nodes, storing raw bytes
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int start = provider.position();
            int flags = provider.readNext() & 0xFF;

            // children array
            int childCount = provider.readVarInt();
            int[] children = new int[childCount];
            for (int c = 0; c < childCount; c++) {
                children[c] = provider.readVarInt();
            }

            // redirect node
            if ((flags & FLAG_HAS_REDIRECT) != 0) {
                provider.readVarInt();
            }

            // name + parser + properties
            if ((flags & 0x03) == TYPE_LITERAL || (flags & 0x03) == TYPE_ARGUMENT) {
                provider.readString(); // name
            }
            if ((flags & 0x03) == TYPE_ARGUMENT) {
                String parser;
                if (parserIsVarInt) {
                    // 1.19+: parser is a VarInt ID
                    int parserId = provider.readVarInt();
                    parser = parserId >= 0 && parserId < parserTable.length
                            ? parserTable[parserId]
                            : "unknown:" + parserId;
                } else {
                    // pre-1.19: parser is a string
                    parser = provider.readString();
                }
                skipParserProperties(provider, parser, protocolVersion);
            }
            // suggestions type — an identifier string if flag 0x10 is set
            if ((flags & FLAG_HAS_SUGGESTIONS) != 0) {
                provider.readString();
            }

            int end = provider.position();
            byte[] raw = extractBytes(provider, start, end);
            nodes.add(new Node(raw, flags, children));
        }

        // Read rootIndex
        int rootIndex = provider.readVarInt();
        if (rootIndex < 0 || rootIndex >= nodes.size()) {
            return true; // invalid, forward original
        }

        // Build modified packet
        int firstNewNode = count;          // index of "world-downloader-proxy"
        int numNewNodes = 1 + SUBCOMMANDS.length;

        PacketBuilder pb = new PacketBuilder("DeclareCommands");

        // Write new node count
        pb.writeVarInt(count + numNewNodes);

        // Write all original nodes, modifying the root to include our command
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (i == rootIndex) {
                // Rewrite root node with added child
                writeNode(pb, node.flags, addChild(node.children, firstNewNode),
                        node.type() != TYPE_ROOT, node);
            } else {
                // Copy raw bytes unchanged
                pb.writeByteArray(node.raw);
            }
        }

        // Write new "world-downloader-proxy" literal node (not executable, has children)
        int[] cmdChildren = new int[SUBCOMMANDS.length];
        for (int s = 0; s < SUBCOMMANDS.length; s++) {
            cmdChildren[s] = firstNewNode + 1 + s;
        }
        writeLiteralNode(pb, TYPE_LITERAL, false, cmdChildren, ROOT_COMMAND);

        // Write subcommand literal nodes (executable, no children)
        for (String sub : SUBCOMMANDS) {
            writeLiteralNode(pb, TYPE_LITERAL | FLAG_EXECUTABLE, true, new int[0], sub);
        }

        // Write rootIndex (unchanged)
        pb.writeVarInt(rootIndex);

        // Send modified packet, drop original
        injector.enqueuePacket(pb);
        return false;
    }

    /**
     * Write a node with the given flags and children. For non-root nodes, also write
     * the name (and parser/properties for argument nodes). For the root node, we only
     * need flags + children (no name).
     */
    private void writeNode(PacketBuilder pb, int flags, int[] children,
                           boolean hasName, Node original) {
        pb.writeByte((byte) flags);
        pb.writeVarInt(children.length);
        for (int c : children) {
            pb.writeVarInt(c);
        }
        // Root nodes have no name/parser, so we're done.
        // For other nodes we already copied raw bytes in the caller, so this method
        // is only used for the root node.
    }

    /** Write a literal node: flags + children + name. */
    private void writeLiteralNode(PacketBuilder pb, int flags, boolean executable,
                                  int[] children, String name) {
        int f = flags;
        if (executable) f |= FLAG_EXECUTABLE;
        pb.writeByte((byte) f);
        pb.writeVarInt(children.length);
        for (int c : children) {
            pb.writeVarInt(c);
        }
        pb.writeString(name);
    }

    private int[] addChild(int[] original, int newChild) {
        int[] result = new int[original.length + 1];
        System.arraycopy(original, 0, result, 0, original.length);
        result[original.length] = newChild;
        return result;
    }

    // --- Position tracking helpers ---

    private byte[] extractBytes(DataTypeProvider provider, int start, int end) {
        return provider.extractBytes(start, end);
    }

    // --- Parser property skipping ---

    /**
     * Skip the properties bytes for the given argument parser.
     * Most parsers have no properties (void). The ones that do are handled explicitly.
     *
     * @param parser the parser identifier (string name, same for pre-1.19 strings and post-1.19 ID mappings)
     * @param protocolVersion the current protocol version (needed for version-dependent properties like minecraft:time)
     */
    private void skipParserProperties(DataTypeProvider provider, String parser, int protocolVersion) {
        switch (parser) {
            // --- void parsers (no properties) ---
            case "brigadier:bool":
            case "minecraft:game_profile":
            case "minecraft:block_pos":
            case "minecraft:column_pos":
            case "minecraft:vec3":
            case "minecraft:vec2":
            case "minecraft:block_state":
            case "minecraft:block_predicate":
            case "minecraft:item_stack":
            case "minecraft:item_predicate":
            case "minecraft:color":
            case "minecraft:hex_color":
            case "minecraft:component":
            case "minecraft:style":
            case "minecraft:message":
            case "minecraft:nbt":
            case "minecraft:nbt_compound_tag":
            case "minecraft:nbt_tag":
            case "minecraft:nbt_path":
            case "minecraft:objective":
            case "minecraft:objective_criteria":
            case "minecraft:operation":
            case "minecraft:particle":
            case "minecraft:angle":
            case "minecraft:rotation":
            case "minecraft:scoreboard_slot":
            case "minecraft:swizzle":
            case "minecraft:team":
            case "minecraft:item_slot":
            case "minecraft:item_slots":
            case "minecraft:resource_location":
            case "minecraft:function":
            case "minecraft:entity_anchor":
            case "minecraft:int_range":
            case "minecraft:float_range":
            case "minecraft:dimension":
            case "minecraft:gamemode":
            case "minecraft:template_mirror":
            case "minecraft:template_rotation":
            case "minecraft:heightmap":
            case "minecraft:loot_table":
            case "minecraft:loot_predicate":
            case "minecraft:loot_modifier":
            case "minecraft:dialog":
            case "minecraft:uuid":
            case "minecraft:mob_effect":
            case "minecraft:item_enchantment":
            case "minecraft:entity_summon":
                // void — no properties
                break;

            // --- numeric parsers with min/max flags ---
            case "brigadier:float":
            case "brigadier:double":
            case "brigadier:integer":
            case "brigadier:long":
                // flags byte: bit 0 = min_present, bit 1 = max_present
                int numFlags = provider.readNext() & 0xFF;
                if ((numFlags & 0x01) != 0) {
                    if (parser.equals("brigadier:float")) provider.skip(4);
                    else if (parser.equals("brigadier:double")) provider.skip(8);
                    else if (parser.equals("brigadier:integer")) provider.skip(4);
                    else provider.skip(8); // long
                }
                if ((numFlags & 0x02) != 0) {
                    if (parser.equals("brigadier:float")) provider.skip(4);
                    else if (parser.equals("brigadier:double")) provider.skip(8);
                    else if (parser.equals("brigadier:integer")) provider.skip(4);
                    else provider.skip(8); // long
                }
                break;

            // --- string parser with mode VarInt ---
            case "brigadier:string":
                // VarInt mode: 0=single_word, 1=quotable_phrase, 2=greedy_phrase
                provider.readVarInt();
                break;

            // --- entity/score_holder with flags byte ---
            case "minecraft:entity":
            case "minecraft:score_holder":
                // single byte flags
                provider.readNext();
                break;

            // --- time parser: void before 1.20, int (min ticks) from 1.20+ ---
            case "minecraft:time":
                if (protocolVersion >= PROTOCOL_1_20) {
                    provider.readInt();
                }
                // else: void (no properties) for pre-1.20
                break;

            // --- resource parsers with registry identifier ---
            case "minecraft:resource_or_tag":
            case "minecraft:resource_or_tag_key":
            case "minecraft:resource":
            case "minecraft:resource_key":
            case "minecraft:resource_selector":
                // Registry identifier (a string)
                provider.readString();
                break;

            // --- range parser (pre-1.19 only, replaced by int_range/float_range in 1.19+) ---
            case "minecraft:range":
                // bool: allowDecimals
                provider.readNext();
                break;

            default:
                // Unknown parser — assume no properties.
                // This is safer than guessing and corrupting the stream.
                System.out.println("[CommandTreeInjector] Unknown parser: " + parser + " — assuming no properties");
                break;
        }
    }
}
