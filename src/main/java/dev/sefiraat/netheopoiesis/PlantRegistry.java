package dev.sefiraat.netheopoiesis;

import dev.sefiraat.netheopoiesis.core.plant.breeding.BreedResult;
import dev.sefiraat.netheopoiesis.core.plant.breeding.BreedResultType;
import dev.sefiraat.netheopoiesis.core.plant.breeding.BreedingPair;
import dev.sefiraat.netheopoiesis.slimefun.flora.seeds.BiomeSpreadingSeed;
import dev.sefiraat.netheopoiesis.slimefun.flora.seeds.CruxSpreadingSeed;
import dev.sefiraat.netheopoiesis.slimefun.flora.seeds.DroppingSeed;
import dev.sefiraat.netheopoiesis.slimefun.flora.seeds.EntitySpawningSeed;
import dev.sefiraat.netheopoiesis.slimefun.flora.seeds.GenericTickingSeed;
import dev.sefiraat.netheopoiesis.slimefun.flora.seeds.HarvestableSeed;
import dev.sefiraat.netheopoiesis.slimefun.flora.seeds.NetherSeed;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang.WordUtils;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PlantRegistry {

    private static PlantRegistry instance;

    private final List<NetherSeed> registeredPlants = new ArrayList<>();
    private final List<BreedingPair> breedingPairs = new ArrayList<>();

    public PlantRegistry() {
        Validate.isTrue(instance == null, "Cannot create a new instance of the PlantRegistry");
        instance = this;
    }

    public void addPlant(@Nonnull NetherSeed netherSeed) {
        this.registeredPlants.add(netherSeed);
        this.breedingPairs.addAll(netherSeed.getBreedingPairs());
    }

    @Nonnull
    public BreedResult getBreedResult(@Nonnull String seed1, @Nonnull String seed2) {
        int matches = 0;
        for (BreedingPair pair : breedingPairs) {
            final BreedResultType result = pair.testBreed(seed1, seed2);
            if (result != BreedResultType.NOT_PAIR) {
                if (result != BreedResultType.FAIL) {
                    return new BreedResult(pair, result);
                } else {
                    matches++;
                }
            }
        }
        return new BreedResult(breedingPairs.get(0), matches == 0 ? BreedResultType.NO_PAIRS : BreedResultType.FAIL);
    }

    @Nonnull
    public List<NetherSeed> getRegisteredPlants() {
        return Collections.unmodifiableList(registeredPlants);
    }

    @Nonnull
    public List<BreedingPair> getBreedingPairs() {
        return Collections.unmodifiableList(breedingPairs);
    }

    /**
     * Should never be used in ac production environment - this method will output all plant details
     * into .md files for GitBook
     */
    public void printRegistry() {
        String template = null;
        try {
            template = new String(
                Netheopoiesis.getInstance().getResource("gitbook-template.md").readAllBytes(),
                StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        if (template == null) {
            return;
        }

        final String path = Netheopoiesis.getInstance().getDataFolder().getAbsolutePath();
        final DecimalFormat format = new DecimalFormat("##.##");

        for (NetherSeed seed : registeredPlants) {
            final StringBuilder places = new StringBuilder();

            for (String s : seed.getPlacements()) {
                places.append("- ")
                      .append(toTitleCase(ChatColor.stripColor(s.replace("NPS_", ""))))
                      .append("\n");
            }

            String output = template;
            output = output.replace("{NAME}", toTitleCase(ChatColor.stripColor(seed.getItemName())));
            output = output.replace("{PLACEMENT_LIST}", places.toString());
            output = output.replace("{GROWTH_RATE_PERCENTAGE}", format.format(seed.getGrowthRate() * 100));
            output = output.replace("{PURIFICATION_VALUE}", String.valueOf(seed.getPurificationValue()));
            output = output.replace("{HASH_STAGE_0}", seed.getGrowthStages().getStages().get(0).getHash());
            output = output.replace("{HASH_STAGE_1}", seed.getGrowthStages().getStages().get(1).getHash());
            output = output.replace("{HASH_STAGE_2}", seed.getGrowthStages().getStages().get(2).getHash());
            output = output.replace("{HASH_STAGE_3}", seed.getGrowthStages().getStages().get(3).getHash());
            output = output.replace("{HASH_STAGE_4}", seed.getGrowthStages().getStages().get(4).getHash());
            output = output.replace("{HASH_STAGE_5}", seed.getGrowthStages().getStages().get(5).getHash());

            if (seed instanceof HarvestableSeed harvestable) {
                final ItemStack itemStack = harvestable.getHarvestingResult();
                final String itemName = itemStack.getItemMeta().hasDisplayName() ?
                                        ChatColor.stripColor(itemStack.getItemMeta().getDisplayName()) :
                                        toTitleCase(itemStack.getType().name());
                output = output.replace("{TYPE_FEATURE}", "Harvesting Tool Output");
                output = output.replace("{TYPE_DESC}", "When harvested, this plant will drop: " + itemName);
            } else if (seed instanceof GenericTickingSeed ticking) {
                output = output.replace("{TYPE_FEATURE}", "On Tick");
                output = output.replace("{TYPE_DESC}", "");
            } else if (seed instanceof EntitySpawningSeed spawning) {
                output = output.replace("{TYPE_FEATURE}", "Spawns Mob");
                output = output.replace("{TYPE_DESC}", toTitleCase(spawning.getEntityType().name()));
            } else if (seed instanceof DroppingSeed dropping) {
                final StringBuilder drops = new StringBuilder();

                dropping.getPossibleDrops().forEach(itemStack -> {
                    final String itemName = itemStack.getItemMeta().hasDisplayName() ?
                                            ChatColor.stripColor(itemStack.getItemMeta().getDisplayName()) :
                                            toTitleCase(itemStack.getType().name());
                    drops.append("- ")
                         .append(itemName)
                         .append("\n");
                });

                output = output.replace("{TYPE_FEATURE}", "Drops Items");
                output = output.replace("{TYPE_DESC}", drops.toString());
            } else if (seed instanceof CruxSpreadingSeed spreading) {
                String additional = "";
                if (spreading instanceof BiomeSpreadingSeed biome) {
                    additional = " and changes the Biome to: " + biome.getBiome().name();
                }
                output = output.replace("{TYPE_FEATURE}", "Purification");
                output = output.replace(
                    "{TYPE_DESC}",
                    "Purifies nearby blocks into: " +
                        ChatColor.stripColor(spreading.getCrux().getDisplayName()) + additional
                );
            }

            String fileName = ChatColor.stripColor(seed.getItemName()).toLowerCase(Locale.ROOT).replace(" ", "-");

            try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(path + "/" + fileName + ".md"))
            ) {
                writer.write(output);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Nonnull
    private String toTitleCase(String string) {
        final char[] delimiters = {' ', '_'};
        return WordUtils.capitalizeFully(string, delimiters).replace("_", " ");
    }

    public static PlantRegistry getInstance() {
        return instance;
    }
}
