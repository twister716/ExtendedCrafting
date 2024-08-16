package com.blakebr0.extendedcrafting;

import com.blakebr0.extendedcrafting.client.ModMenuScreens;
import com.blakebr0.extendedcrafting.client.ModRecipeBookCategories;
import com.blakebr0.extendedcrafting.client.ModTESRs;
import com.blakebr0.extendedcrafting.client.handler.ColorHandler;
import com.blakebr0.extendedcrafting.config.ModConfigs;
import com.blakebr0.extendedcrafting.crafting.DynamicRecipeManager;
import com.blakebr0.extendedcrafting.init.ModBlocks;
import com.blakebr0.extendedcrafting.init.ModCreativeModeTabs;
import com.blakebr0.extendedcrafting.init.ModDataComponentTypes;
import com.blakebr0.extendedcrafting.init.ModItems;
import com.blakebr0.extendedcrafting.init.ModLootItemFunctionTypes;
import com.blakebr0.extendedcrafting.init.ModMenuTypes;
import com.blakebr0.extendedcrafting.init.ModRecipeSerializers;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.blakebr0.extendedcrafting.init.ModReloadListeners;
import com.blakebr0.extendedcrafting.init.ModTileEntities;
import com.blakebr0.extendedcrafting.singularity.SingularityRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ExtendedCrafting.MOD_ID)
public final class ExtendedCrafting {
	public static final String MOD_ID = "extendedcrafting";
	public static final String NAME = "Extended Crafting";
	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

	public ExtendedCrafting(IEventBus bus, ModContainer mod) {
		bus.register(this);

		ModBlocks.REGISTRY.register(bus);
		ModItems.REGISTRY.register(bus);
		ModCreativeModeTabs.REGISTRY.register(bus);
		ModDataComponentTypes.REGISTRY.register(bus);
		ModLootItemFunctionTypes.REGISTRY.register(bus);
		ModTileEntities.REGISTRY.register(bus);
		ModMenuTypes.REGISTRY.register(bus);
		ModRecipeTypes.REGISTRY.register(bus);
		ModRecipeSerializers.REGISTRY.register(bus);

		if (FMLEnvironment.dist == Dist.CLIENT) {
			bus.register(new ColorHandler());
			bus.register(new ModMenuScreens());
			bus.register(new ModRecipeBookCategories());
			bus.register(new ModTESRs());
		}

		mod.registerConfig(ModConfig.Type.CLIENT, ModConfigs.CLIENT);
		mod.registerConfig(ModConfig.Type.STARTUP, ModConfigs.COMMON, "extendedcrafting-common.toml");
	}

	@SubscribeEvent
	public void onCommonSetup(FMLCommonSetupEvent event) {
		NeoForge.EVENT_BUS.register(new ModReloadListeners());
		NeoForge.EVENT_BUS.register(DynamicRecipeManager.getInstance());
		NeoForge.EVENT_BUS.register(SingularityRegistry.getInstance());

		SingularityRegistry.getInstance().writeDefaultSingularityFiles();
	}

	public static ResourceLocation resource(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
