package eu.pb4.polymertest.util;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.resourcepack.extras.api.format.blockstate.BlockStateAsset;
import eu.pb4.polymer.resourcepack.extras.api.format.blockstate.StateModelVariant;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.predicate.block.BlockStatePredicate;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class UnusedBlockStateDetector {

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Path clientJarRoot = PolymerCommonUtils.getClientJarRoot();
            Path blockStatesBase = clientJarRoot.resolve("/assets/minecraft/blockstates/");
            try {
                Map<String, Integer> map = new HashMap<>();
                Files.walk(blockStatesBase).forEach(path -> {
                    if (!path.toString().endsWith(".json")) {
                        return;
                    }
//                    System.out.println(path.toString());
                    String idPath = path.toString().substring("/assets/minecraft/blockstates/".length()).replace(".json", "");
                    if (idPath.contains("stairs") || idPath.contains("trapdoor") || idPath.contains("leaves") || idPath.contains("sapling") || idPath.contains("door") || idPath.contains("sign")) return;
//                    System.out.println(idPath);
                    try {
                        Block block = Registries.BLOCK.get(Identifier.of(idPath));
//                        if (block instanceof BlockWithEntity) return;
                        Set<List<StateModelVariant>> variantsSet = new java.util.HashSet<>();
                        AtomicInteger duplicateStates = new AtomicInteger();

                        BlockStateAsset decoded = BlockStateAsset.CODEC.decode(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(path))).getOrThrow().getFirst();
                        if (decoded.variants().isEmpty()) return;
                        List<Pair<BlockStatePredicate, List<StateModelVariant>>> variants = parseVariants(block, decoded.variants().get());
                        block.getStateManager().getStates().forEach(state -> {
                            if (state.get(Properties.WATERLOGGED, false)) return;
                            for (Pair<BlockStatePredicate, List<StateModelVariant>> variant : variants) {
                                if (variant.getLeft().test(state)) {
                                    List<StateModelVariant> right = variant.getRight();
                                    if (variantsSet.contains(right)) {
                                        duplicateStates.getAndIncrement();
                                    } else {
                                        variantsSet.add(right);
                                    }
                                    break;
                                }
                            }
                        });
                        if (duplicateStates.get() > 0) {
                            map.put(idPath, duplicateStates.get());
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                });
                map.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).forEach(entry -> {
                    System.out.println(entry.getKey() + " has " + entry.getValue() + " duplicate states!");
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static List<Pair<BlockStatePredicate, List<StateModelVariant>>> parseVariants(Block block, Map<String, List<StateModelVariant>> modelDef) {
        List<Pair<BlockStatePredicate, List<StateModelVariant>>> result = new java.util.ArrayList<>();
        start:
        for (var pair : modelDef.entrySet()) {
            var stateMap = pair.getKey().split(",");

            var predicate = BlockStatePredicate.forBlock(block);


            for (var statePair : stateMap) {
                if (!statePair.isEmpty()) {
                    var split = statePair.split("=", 2);
                    var prop = (Property) block.getStateManager().getProperty(split[0]);

                    if (prop == null) {
                        continue start;
                    }

                    predicate.with(prop, x -> prop.name((Comparable) x).equals(split[1]));
                }
            }

            result.add(new Pair<>(predicate, pair.getValue()));
        }
        return result;
    }

}
