package com.bigback.client;

import com.google.gson.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.entity.*;
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.glfw.GLFW;

// ===== HackClient.java =====
/** BigBack Client entrypoint. */
public class HackClient implements ClientModInitializer {
    public static final List<Module> MODULES = new ArrayList<>();
    public static final Watermark WATERMARK = new Watermark();
    public static final InfoHud INFO = new InfoHud();
    public static final FindsHud FINDS = new FindsHud();
    public static final ArmorHud ARMOR = new ArmorHud();

    private static KeyBinding openGui;
    private Object lastWorld;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("bigback", "main"));
        openGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bigback.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));

        // Combat
        MODULES.add(new AutoTotem(GLFW.GLFW_KEY_Z));
        MODULES.add(new AutoCrystal(GLFW.GLFW_KEY_C));
        MODULES.add(new AutoAnchor(GLFW.GLFW_KEY_V));
        MODULES.add(new KillAura(GLFW.GLFW_KEY_K));
        MODULES.add(new TriggerBot(GLFW.GLFW_KEY_H));
        MODULES.add(new AutoArmor(GLFW.GLFW_KEY_UNKNOWN));
        MODULES.add(new AutoWeapon(GLFW.GLFW_KEY_UNKNOWN));

        // Movement
        MODULES.add(new Module("Sprint", Category.MOVEMENT, GLFW.GLFW_KEY_R) {
            @Override public void onTick(MinecraftClient mc) {
                if (mc.player != null && mc.player.input.playerInput.forward() && !mc.player.horizontalCollision)
                    mc.player.setSprinting(true);
            }
        });
        MODULES.add(new Module("Flight", Category.MOVEMENT, GLFW.GLFW_KEY_G) {
            @Override public void onTick(MinecraftClient mc) {
                if (mc.player != null) mc.player.getAbilities().allowFlying = true;
            }
            @Override public void onDisable(MinecraftClient mc) {
                if (mc.player != null && !mc.player.isCreative()) {
                    mc.player.getAbilities().allowFlying = false;
                    mc.player.getAbilities().flying = false;
                }
            }
        });
        MODULES.add(new Speed(GLFW.GLFW_KEY_J));
        MODULES.add(new Step(GLFW.GLFW_KEY_N));
        MODULES.add(new NoFall(GLFW.GLFW_KEY_M));
        MODULES.add(new ElytraBoost(GLFW.GLFW_KEY_UNKNOWN));
        MODULES.add(new AutoWalk(GLFW.GLFW_KEY_UNKNOWN));
        MODULES.add(new Parkour(GLFW.GLFW_KEY_UNKNOWN));

        // Player
        MODULES.add(new AutoTool(GLFW.GLFW_KEY_UNKNOWN));
        MODULES.add(new AutoRespawn(GLFW.GLFW_KEY_UNKNOWN));
        MODULES.add(new ChestStealer(GLFW.GLFW_KEY_UNKNOWN));
        MODULES.add(new AutoEat(GLFW.GLFW_KEY_UNKNOWN));
        MODULES.add(new AntiAFK(GLFW.GLFW_KEY_UNKNOWN));

        // World (base finding)
        MODULES.add(new StorageFinder());
        MODULES.add(new BuildFinder());
        MODULES.add(new SpawnerFinder());
        MODULES.add(new PortalFinder());
        MODULES.add(new SignFinder());
        MODULES.add(new BedFinder());
        MODULES.add(new UtilityFinder());
        MODULES.add(new FrameFinder());
        MODULES.add(FINDS);

        // Render
        MODULES.add(new Module("Fullbright", Category.RENDER, GLFW.GLFW_KEY_B) {
            @Override public void onTick(MinecraftClient mc) {
                if (mc.player == null) return;
                StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
                if (cur == null || cur.getDuration() < 300)
                    mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false, false));
            }
            @Override public void onDisable(MinecraftClient mc) {
                if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
        });
        MODULES.add(new Zoom(GLFW.GLFW_KEY_X));
        MODULES.add(ARMOR);
        MODULES.add(WATERMARK);
        MODULES.add(INFO);

        Hud.init();
        Config.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Config.save());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGui.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new ClickGUI());
            }
            if (client.world != null && client.world != lastWorld) {
                lastWorld = client.world;
                Finds.switchTo(client);
            }
            for (Module m : MODULES) m.handleKey(client);
            for (Module m : MODULES) if (m.enabled) m.onTick(client);
        });
    }
}

// ===== AntiAFK.java =====
class AntiAFK extends Module {
    private final NumberSetting interval = number("Interval (s)", 20, 5, 120, 5);
    private final Random rng = new Random();
    private int timer;

    public AntiAFK(int key) { super("AntiAFK", Category.PLAYER, key); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.currentScreen != null) return;
        if (timer-- > 0) return;
        timer = interval.intValue() * 20;
        if (mc.player.isOnGround()) mc.player.jump();
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.setYaw(mc.player.getYaw() + (rng.nextFloat() - 0.5f) * 20f);
    }
}

// ===== ArmorHud.java =====
/** Armor pieces with durability bars, plus your totem count, in the bottom-right corner. */
class ArmorHud extends Module {
    final BoolSetting totems = bool("Totem Count", true);

    public ArmorHud() {
        super("Armor HUD", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
        enabled = true;
        hidden = true;
    }
}

// ===== AutoAnchor.java =====
class AutoAnchor extends Module {
    private final NumberSetting range = number("Range", 4.5, 1, 6, 0.1);
    private final NumberSetting delay = number("Delay", 2, 0, 10, 1);
    private final NumberSetting minDamage = number("Min Damage", 6, 1, 20, 1);
    private final NumberSetting maxSelf = number("Max Self", 4, 0, 20, 1);
    private int timer;

    public AutoAnchor(int key) { super("AutoAnchor", Category.COMBAT, key); }

    private void use(MinecraftClient mc, BlockPos pos, Direction face) {
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), face, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean selfOk(MinecraftClient mc, BlockPos anchor) {
        float self = Damage.explosion(mc.player, Vec3d.ofCenter(anchor), Damage.ANCHOR_POWER, anchor);
        return self <= maxSelf.value && self < Damage.effectiveHealth(mc.player) - 1f;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.world.getRegistryKey() == World.NETHER) return; // Nether: anchors don't explode
        if (timer-- > 0) return;

        PlayerEntity target = Util.nearestPlayer(mc, 8);
        if (target == null) return;
        int anchorSlot = Util.findHotbar(mc, Items.RESPAWN_ANCHOR);
        int glowSlot = Util.findHotbar(mc, Items.GLOWSTONE);
        if (glowSlot == -1) return;

        BlockPos base = target.getBlockPos();
        BlockPos anchor = null;
        for (int x = -3; x <= 3 && anchor == null; x++)
            for (int y = -1; y <= 3 && anchor == null; y++)
                for (int z = -3; z <= 3; z++) {
                    BlockPos p = base.add(x, y, z);
                    if (mc.world.getBlockState(p).isOf(Blocks.RESPAWN_ANCHOR)) { anchor = p; break; }
                }

        if (anchor != null) {
            if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(anchor)) > range.value) return;
            if (!selfOk(mc, anchor)) return;
            float dmg = Damage.explosion(target, Vec3d.ofCenter(anchor), Damage.ANCHOR_POWER, anchor);
            if (dmg < minDamage.value) return;

            if (mc.world.getBlockState(anchor).get(RespawnAnchorBlock.CHARGES) < 1) {
                Util.select(mc, glowSlot);
            } else {
                for (int i = 0; i < 9; i++) if (i != glowSlot) { Util.select(mc, i); break; }
            }
            use(mc, anchor, Direction.UP);
            timer = delay.intValue();
            return;
        }

        if (anchorSlot == -1) return;
        BlockPos bestSupport = null;
        float bestDmg = 0;
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                BlockPos support = base.add(x, -1, z);
                BlockPos place = support.up();
                if (!mc.world.getBlockState(support).isSolidBlock(mc.world, support)) continue;
                if (!mc.world.isAir(place) || !mc.world.isAir(place.up())) continue;
                if (place.equals(base) || place.equals(base.up())) continue;
                if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(support)) > range.value) continue;
                if (!selfOk(mc, place)) continue;
                float dmg = Damage.explosion(target, Vec3d.ofCenter(place), Damage.ANCHOR_POWER, place);
                if (dmg >= minDamage.value && dmg > bestDmg) { bestDmg = dmg; bestSupport = support; }
            }
        if (bestSupport != null) {
            Util.select(mc, anchorSlot);
            use(mc, bestSupport, Direction.UP);
            timer = delay.intValue();
        }
    }
}

// ===== AutoArmor.java =====
class AutoArmor extends Module {
    private final NumberSetting delay = number("Delay", 2, 0, 10, 1);
    private int timer;

    public AutoArmor(int key) { super("AutoArmor", Category.COMBAT, key); }

    private static String path(ItemStack s) { return Registries.ITEM.getId(s.getItem()).getPath(); }

    /** 0 head, 1 chest, 2 legs, 3 feet, -1 not armor */
    private static int armorSlot(ItemStack s) {
        String p = path(s);
        if (p.endsWith("_helmet")) return 0;
        if (p.endsWith("_chestplate")) return 1;
        if (p.endsWith("_leggings")) return 2;
        if (p.endsWith("_boots")) return 3;
        return -1;
    }

    private static int score(ItemStack s) {
        String p = path(s);
        if (p.startsWith("netherite_")) return 6;
        if (p.startsWith("diamond_")) return 5;
        if (p.startsWith("iron_")) return 4;
        if (p.startsWith("chainmail_")) return 3;
        if (p.startsWith("golden_") || p.startsWith("turtle_")) return 2;
        return 1;
    }

    private void click(MinecraftClient mc, int syncId, int slot) {
        mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (timer-- > 0) return;

        var h = mc.player.playerScreenHandler;
        for (int slot = 9; slot <= 44; slot++) {
            ItemStack stack = h.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;
            int type = armorSlot(stack);
            if (type == -1) continue;

            int armorId = 5 + type;
            ItemStack worn = h.getSlot(armorId).getStack();
            if (!worn.isEmpty() && score(stack) <= score(worn)) continue;

            click(mc, h.syncId, slot);
            click(mc, h.syncId, armorId);
            if (!worn.isEmpty()) click(mc, h.syncId, slot);
            timer = delay.intValue();
            return;
        }
    }
}

// ===== AutoCrystal.java =====
class AutoCrystal extends Module {
    private final NumberSetting range = number("Range", 4.5, 1, 6, 0.1);
    private final NumberSetting delay = number("Delay", 2, 0, 10, 1);
    private final NumberSetting minDamage = number("Min Damage", 6, 1, 20, 1);
    private final NumberSetting maxSelf = number("Max Self", 4, 0, 20, 1);
    private final BoolSetting breakCrystals = bool("Break", true);
    private final BoolSetting place = bool("Place", true);
    private int timer;

    public AutoCrystal(int key) { super("AutoCrystal", Category.COMBAT, key); }

    private boolean safe(MinecraftClient mc, float selfDmg) {
        return selfDmg <= maxSelf.value && selfDmg < Damage.effectiveHealth(mc.player) - 1f;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (timer-- > 0) return;

        PlayerEntity target = Util.nearestPlayer(mc, 10);
        if (target == null) return;

        if (breakCrystals.value) {
            EndCrystalEntity best = null;
            float bestDmg = 0;
            for (var e : mc.world.getEntities()) {
                if (!(e instanceof EndCrystalEntity c)) continue;
                if (mc.player.squaredDistanceTo(c) > range.value * range.value) continue;
                Vec3d center = c.getPos().add(0, 0.0625, 0);
                if (!safe(mc, Damage.explosion(mc.player, center, Damage.CRYSTAL_POWER, null))) continue;
                float dmg = Damage.explosion(target, center, Damage.CRYSTAL_POWER, null);
                if (dmg >= minDamage.value && dmg > bestDmg) { bestDmg = dmg; best = c; }
            }
            if (best != null) {
                mc.interactionManager.attackEntity(mc.player, best);
                mc.player.swingHand(Hand.MAIN_HAND);
                timer = delay.intValue();
                return;
            }
        }

        if (!place.value) return;
        int slot = Util.findHotbar(mc, Items.END_CRYSTAL);
        if (slot == -1) return;

        BlockPos bestPos = null;
        float bestDmg = 0;
        BlockPos base = target.getBlockPos();
        for (int x = -4; x <= 4; x++)
            for (int y = -3; y <= 1; y++)
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = base.add(x, y, z);
                    var block = mc.world.getBlockState(pos).getBlock();
                    if (block != Blocks.OBSIDIAN && block != Blocks.BEDROCK) continue;
                    if (!mc.world.isAir(pos.up()) || !mc.world.isAir(pos.up(2))) continue;
                    if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) > range.value) continue;
                    Box box = new Box(pos.getX(), pos.getY() + 1, pos.getZ(),
                                      pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1);
                    if (!mc.world.getOtherEntities(null, box).isEmpty()) continue;

                    Vec3d center = Vec3d.of(pos).add(0.5, 1.0625, 0.5);
                    if (!safe(mc, Damage.explosion(mc.player, center, Damage.CRYSTAL_POWER, null))) continue;
                    float dmg = Damage.explosion(target, center, Damage.CRYSTAL_POWER, null);
                    if (dmg >= minDamage.value && dmg > bestDmg) { bestDmg = dmg; bestPos = pos; }
                }
        if (bestPos == null) return;

        Util.select(mc, slot);
        BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(bestPos).add(0, 0.5, 0), Direction.UP, bestPos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        timer = delay.intValue();
    }
}

// ===== AutoEat.java =====
/** Eats safe food from your hotbar when hunger drops, then switches back. */
class AutoEat extends Module {
    private final NumberSetting eatAt = number("Eat At", 14, 1, 19, 1);
    private boolean eating;
    private int prevSlot = -1, ticks;

    public AutoEat(int key) { super("AutoEat", Category.PLAYER, key); }

    private static boolean edible(ItemStack s) {
        return !s.isEmpty() && s.get(DataComponentTypes.FOOD) != null
                && !s.isOf(Items.GOLDEN_APPLE) && !s.isOf(Items.ENCHANTED_GOLDEN_APPLE)
                && !s.isOf(Items.ROTTEN_FLESH) && !s.isOf(Items.SPIDER_EYE)
                && !s.isOf(Items.POISONOUS_POTATO) && !s.isOf(Items.PUFFERFISH)
                && !s.isOf(Items.CHORUS_FRUIT) && !s.isOf(Items.SUSPICIOUS_STEW);
    }

    private void stop(MinecraftClient mc) {
        mc.options.useKey.setPressed(false);
        if (prevSlot != -1) Util.select(mc, prevSlot);
        prevSlot = -1; eating = false; ticks = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;
        int food = mc.player.getHungerManager().getFoodLevel();
        if (eating) {
            ticks++;
            if (food >= 20 || (ticks > 5 && !mc.player.isUsingItem())) stop(mc);
            return;
        }
        if (mc.currentScreen != null || food > eatAt.intValue()) return;
        for (int i = 0; i < 9; i++) {
            if (edible(mc.player.getInventory().getStack(i))) {
                prevSlot = Util.selected(mc);
                Util.select(mc, i);
                mc.options.useKey.setPressed(true);
                eating = true; ticks = 0;
                return;
            }
        }
    }

    @Override
    public void onDisable(MinecraftClient mc) { if (eating) stop(mc); }
}

// ===== AutoRespawn.java =====
class AutoRespawn extends Module {
    public AutoRespawn(int key) { super("AutoRespawn", Category.PLAYER, key); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player != null && mc.player.isDead()) mc.player.requestRespawn();
    }
}

// ===== AutoTool.java =====
class AutoTool extends Module {
    public AutoTool(int key) { super("AutoTool", Category.PLAYER, key); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (!mc.options.attackKey.isPressed()) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        var inv = mc.player.getInventory();
        int best = inv.getSelectedSlot();
        float bestSpeed = inv.getStack(best).getMiningSpeedMultiplier(state);
        for (int i = 0; i < 9; i++) {
            float s = inv.getStack(i).getMiningSpeedMultiplier(state);
            if (s > bestSpeed) { bestSpeed = s; best = i; }
        }
        Util.select(mc, best);
    }
}

// ===== AutoTotem.java =====
class AutoTotem extends Module {
    private final NumberSetting delay = number("Delay", 2, 0, 10, 1);
    private int timer;

    public AutoTotem(int key) { super("AutoTotem", Category.COMBAT, key); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (timer-- > 0) return;
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        int inv = Util.findInventory(mc, Items.TOTEM_OF_UNDYING);
        if (inv == -1) return;

        int slotId = inv < 9 ? inv + 36 : inv;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slotId, 40,
                SlotActionType.SWAP, mc.player); // button 40 = swap with offhand
        timer = delay.intValue();
    }
}

// ===== AutoWalk.java =====
class AutoWalk extends Module {
    private final BoolSetting sprint = bool("Sprint", true);

    public AutoWalk(int key) { super("AutoWalk", Category.MOVEMENT, key); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.currentScreen != null) return;
        mc.options.forwardKey.setPressed(true);
        if (sprint.value) mc.options.sprintKey.setPressed(true);
    }

    @Override
    public void onDisable(MinecraftClient mc) {
        mc.options.forwardKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }
}

// ===== AutoWeapon.java =====
/** Switches to your best sword (or axe) when you attack something. */
class AutoWeapon extends Module {
    public AutoWeapon(int key) { super("AutoWeapon", Category.COMBAT, key); }

    private static int material(String p) {
        if (p.startsWith("netherite_")) return 6;
        if (p.startsWith("diamond_")) return 5;
        if (p.startsWith("iron_")) return 4;
        if (p.startsWith("stone_")) return 3;
        if (p.startsWith("golden_")) return 2;
        return 1;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.currentScreen != null || !mc.options.attackKey.isPressed()) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult)) return;

        int best = -1, bestScore = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            String p = Registries.ITEM.getId(s.getItem()).getPath();
            int score = p.endsWith("_sword") ? 10 + material(p) : p.endsWith("_axe") ? material(p) : 0;
            if (score > bestScore) { bestScore = score; best = i; }
        }
        if (best != -1) Util.select(mc, best);
    }
}

// ===== BedFinder.java =====
/** Beds cluster in bases (and villages, so expect some false positives). */
class BedFinder extends ScannerModule {
    public BedFinder() { super("BedFinder", "Beds", 3, 20); }

    @Override
    protected int score(WorldChunk chunk, MinecraftClient mc) {
        int s = 0;
        for (BlockEntity be : chunk.getBlockEntities().values())
            if (be instanceof BedBlockEntity) { s++; rep = be.getPos(); }
        return s;
    }
}

// ===== BuildFinder.java =====
/**
 * Counts blocks that rarely generate naturally (planks, wool, concrete, glass, redstone parts, beds...).
 * Redstone/utility blocks count triple. Villages and other structures also trigger it, so keep the
 * threshold fairly high (default 80).
 */
class BuildFinder extends ScannerModule {
    private static final Set<Block> WEAK = new HashSet<>();
    private static final Set<Block> STRONG = new HashSet<>();

    static {
        for (Block b : Registries.BLOCK) {
            String p = Registries.BLOCK.getId(b).getPath();
            if (p.endsWith("_planks") || p.endsWith("_wool") || p.endsWith("_concrete") || p.endsWith("glass")
                    || p.endsWith("glass_pane") || p.endsWith("glazed_terracotta") || p.equals("torch")
                    || p.equals("crafting_table") || p.equals("furnace") || p.equals("bookshelf")) WEAK.add(b);
            if (p.endsWith("_bed") || p.equals("repeater") || p.equals("comparator") || p.equals("observer")
                    || p.equals("piston") || p.equals("sticky_piston") || p.equals("dropper")
                    || p.equals("dispenser") || p.equals("redstone_lamp") || p.equals("note_block")
                    || p.equals("tnt") || p.equals("beacon") || p.equals("enchanting_table")) STRONG.add(b);
        }
        STRONG.add(Blocks.HOPPER);
    }

    public BuildFinder() { super("BuildFinder", "Build", 80, 400); }

    @Override
    protected int score(WorldChunk chunk, MinecraftClient mc) {
        int weak = 0, strong = 0;
        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty()) continue;
            if (!sec.hasAny(st -> WEAK.contains(st.getBlock()) || STRONG.contains(st.getBlock()))) continue;
            int baseY = (chunk.getBottomSectionCoord() + i) << 4;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                Block b = sec.getBlockState(x, y, z).getBlock();
                boolean s = STRONG.contains(b);
                if (s) strong++; else if (WEAK.contains(b)) weak++; else continue;
                if (rep == null || s) rep = new BlockPos(chunk.getPos().getStartX() + x, baseY + y, chunk.getPos().getStartZ() + z);
            }
        }
        return weak + strong * 3;
    }
}

// ===== Category.java =====
enum Category {
    COMBAT("Combat"), MOVEMENT("Movement"), PLAYER("Player"), WORLD("World"), RENDER("Render");

    public final String label;
    Category(String label) { this.label = label; }
}

// ===== ChestStealer.java =====
class ChestStealer extends Module {
    private final NumberSetting delay = number("Delay", 2, 0, 10, 1);
    private final BoolSetting autoClose = bool("Auto Close", true);
    private int timer;

    public ChestStealer(int key) { super("ChestStealer", Category.PLAYER, key); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
        if (timer-- > 0) return;
        if (mc.player.getInventory().getEmptySlot() == -1) return; // inventory full

        var handler = screen.getScreenHandler();
        int containerSlots = handler.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) {
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                timer = delay.intValue();
                return;
            }
        }
        if (autoClose.value) mc.player.closeHandledScreen();
    }
}

// ===== ClickGUI.java =====
/**
 * One window: categories on the left, modules in the middle, settings on the right.
 * Click a module to select it, click its switch to toggle. Type to search. Scroll the list.
 * Settings pane: Enabled switch, Keybind (click, then press a key; Esc/Del clears), then settings.
 */
class ClickGUI extends Screen {
    private static final int TITLE = 28, SIDE = 100, LIST = 180, ROW = 22, SROW = 22, NROW = 32;
    private static final int LINE = 0xFF2A2A3A, SIDEBAR_BG = 0xFF14141C, TITLE_BG = 0xFF101018, FIELD = 0xFF22222E;

    private static Category selCat = Category.COMBAT;
    private static Module selMod;
    private static double scroll;

    private int wx, wy, ww, wh;
    private String search = "";
    private NumberSetting draggingSlider;
    private int sliderX, sliderW;
    private boolean listeningKey;

    public ClickGUI() { super(Text.literal("BigBack Client")); }

    @Override public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        ww = Math.min(500, width - 16);
        wh = Math.min(300, height - 16);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
        if (selMod == null) selMod = firstIn(selCat);
    }

    private Module firstIn(Category c) {
        for (Module m : HackClient.MODULES) if (m.category == c) return m;
        return null;
    }

    private List<Module> visible() {
        List<Module> out = new ArrayList<>();
        String q = search.toLowerCase();
        for (Module m : HackClient.MODULES) {
            if (q.isEmpty() ? m.category == selCat : m.name.toLowerCase().contains(q)) out.add(m);
        }
        return out;
    }

    private int count(Category c) {
        int n = 0;
        for (Module m : HackClient.MODULES) if (m.category == c) n++;
        return n;
    }

    private record SRow(int kind, Setting<?> s, int y, int h) {} // 0 enabled, 1 bind, 2 bool, 3 number

    private List<SRow> settingRows() {
        List<SRow> out = new ArrayList<>();
        if (selMod == null) return out;
        int y = wy + TITLE + 40;
        out.add(new SRow(0, null, y, SROW)); y += SROW;
        out.add(new SRow(1, null, y, SROW)); y += SROW + 8;
        for (Setting<?> s : selMod.settings) {
            boolean num = s instanceof NumberSetting;
            int h = num ? NROW : SROW;
            out.add(new SRow(num ? 3 : 2, s, y, h));
            y += h;
        }
        return out;
    }

    private static String keyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "None";
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        switch (key) {
            case GLFW.GLFW_KEY_SPACE: return "Space";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_BACKSPACE: return "Bksp";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "LShift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "RShift";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RCtrl";
            case GLFW.GLFW_KEY_LEFT_ALT: return "LAlt";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "RAlt";
            case GLFW.GLFW_KEY_UP: return "Up";
            case GLFW.GLFW_KEY_DOWN: return "Down";
            case GLFW.GLFW_KEY_LEFT: return "Left";
            case GLFW.GLFW_KEY_RIGHT: return "Right";
            case GLFW.GLFW_KEY_INSERT: return "Ins";
            case GLFW.GLFW_KEY_HOME: return "Home";
            case GLFW.GLFW_KEY_END: return "End";
            case GLFW.GLFW_KEY_PAGE_UP: return "PgUp";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "PgDn";
            default: break;
        }
        String n = GLFW.glfwGetKeyName(key, 0);
        return n != null ? n.toUpperCase() : "Key" + key;
    }

    private static String format(NumberSetting n) {
        if (n.step >= 1) return String.valueOf(n.intValue());
        return String.format("%.2f", n.value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private int listTop() { return wy + TITLE + 6; }
    private int sx() { return wx + SIDE + LIST + 1; }
    private int sw() { return wx + ww - sx(); }
    private double maxScroll() { return Math.max(0, visible().size() * ROW + 12 - (wh - TITLE)); }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        var tr = MinecraftClient.getInstance().textRenderer;
        ctx.fill(0, 0, width, height, 0x77000000);

        // window + border
        Theme.rounded(ctx, wx - 1, wy - 1, wx + ww + 1, wy + wh + 1, LINE);
        Theme.rounded(ctx, wx, wy, wx + ww, wy + wh, Theme.PANEL_BG);

        // title bar
        ctx.fill(wx, wy, wx + ww, wy + TITLE, TITLE_BG);
        Theme.hGradient(ctx, wx, wy, wx + ww, wy + 2, Theme.animated(0f), Theme.animated(0.4f));
        Theme.gradientText(ctx, tr, "BigBack Client", wx + 12, wy + 10, 0f);

        int sbW = 130, sbX = wx + ww - sbW - 10, sbY = wy + 6;
        Theme.rounded(ctx, sbX, sbY, sbX + sbW, sbY + 17, FIELD);
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        if (search.isEmpty()) ctx.drawTextWithShadow(tr, "Type to search", sbX + 6, sbY + 5, Theme.TEXT_DIM);
        else ctx.drawTextWithShadow(tr, search + (blink ? "_" : ""), sbX + 6, sbY + 5, Theme.TEXT);

        renderSidebar(ctx, tr, mouseX, mouseY);
        renderList(ctx, tr, mouseX, mouseY);
        renderSettings(ctx, tr, mouseX, mouseY);
    }

    private void renderSidebar(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, int mx, int my) {
        ctx.fill(wx, wy + TITLE, wx + SIDE, wy + wh, SIDEBAR_BG);
        ctx.fill(wx + SIDE, wy + TITLE, wx + SIDE + 1, wy + wh, LINE);

        int y = wy + TITLE + 10;
        for (Category c : Category.values()) {
            int col = Theme.category(c);
            boolean sel = c == selCat && search.isEmpty();
            boolean hover = mx >= wx && mx < wx + SIDE && my >= y && my < y + 24;
            if (sel) {
                Theme.hGradient(ctx, wx, y, wx + SIDE, y + 24, Theme.alpha(col, 0x55), Theme.alpha(col, 0x08));
                ctx.fill(wx, y, wx + 2, y + 24, col);
            } else if (hover) ctx.fill(wx, y, wx + SIDE, y + 24, Theme.HOVER);

            Theme.rounded(ctx, wx + 12, y + 8, wx + 20, y + 16, col);
            ctx.drawTextWithShadow(tr, c.label, wx + 26, y + 8, sel ? Theme.TEXT : Theme.TEXT_DIM);
            String n = String.valueOf(count(c));
            ctx.drawTextWithShadow(tr, n, wx + SIDE - 10 - tr.getWidth(n), y + 8, Theme.TEXT_DIM);
            y += 26;
        }
        ctx.drawTextWithShadow(tr, "RShift to close", wx + 12, wy + wh - 14, Theme.TEXT_DIM);
    }

    private void renderList(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, int mx, int my) {
        int lx = wx + SIDE + 1;
        List<Module> mods = visible();
        ctx.enableScissor(lx, wy + TITLE, lx + LIST - 1, wy + wh);

        if (mods.isEmpty()) ctx.drawTextWithShadow(tr, "No modules found", lx + 14, listTop() + 6, Theme.TEXT_DIM);

        int y = (int) (listTop() - scroll);
        for (Module m : mods) {
            int col = Theme.category(m.category);
            boolean sel = m == selMod;
            boolean hover = mx >= lx && mx < lx + LIST - 1 && my >= y && my < y + ROW - 2 && my > wy + TITLE;
            int x1 = lx + 6, x2 = lx + LIST - 8;

            if (sel) Theme.rounded(ctx, x1, y, x2, y + ROW - 2, 0xFF262636);
            else if (hover) Theme.rounded(ctx, x1, y, x2, y + ROW - 2, Theme.HOVER);
            if (m.enabled) ctx.fill(x1, y + 3, x1 + 2, y + ROW - 5, col);

            ctx.drawTextWithShadow(tr, m.name, x1 + 8, y + 6, m.enabled ? Theme.TEXT : Theme.TEXT_DIM);

            int pillX = x2 - 26;
            Theme.pill(ctx, pillX, y + 4, m.enabled, col);
            if (m.key != GLFW.GLFW_KEY_UNKNOWN) {
                String tag = keyName(m.key);
                ctx.drawTextWithShadow(tr, tag, pillX - 6 - tr.getWidth(tag), y + 6, 0xFF5E5E72);
            }
            y += ROW;
        }
        ctx.disableScissor();
    }

    private void renderSettings(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, int mx, int my) {
        int sx = sx(), sw = sw();
        if (selMod == null) return;
        int col = Theme.category(selMod.category);

        ctx.drawTextWithShadow(tr, selMod.name, sx + 12, wy + TITLE + 10, col);
        ctx.drawTextWithShadow(tr, selMod.category.label, sx + 12, wy + TITLE + 22, Theme.TEXT_DIM);
        ctx.fill(sx + 10, wy + TITLE + 35, sx + sw - 10, wy + TITLE + 36, LINE);

        for (SRow r : settingRows()) {
            boolean hover = mx >= sx && mx < sx + sw && my >= r.y() && my < r.y() + r.h();
            if (hover) ctx.fill(sx + 4, r.y(), sx + sw - 4, r.y() + r.h(), 0x14FFFFFF);
            int right = sx + sw - 12;

            switch (r.kind()) {
                case 0 -> {
                    ctx.drawTextWithShadow(tr, "Enabled", sx + 12, r.y() + 7, Theme.TEXT);
                    Theme.pill(ctx, right - 20, r.y() + 6, selMod.enabled, col);
                }
                case 1 -> {
                    ctx.drawTextWithShadow(tr, "Keybind", sx + 12, r.y() + 7, Theme.TEXT);
                    String v = listeningKey ? "Press a key..." : keyName(selMod.key);
                    int bw = Math.max(46, tr.getWidth(v) + 12);
                    Theme.rounded(ctx, right - bw, r.y() + 3, right, r.y() + SROW - 3, listeningKey ? Theme.alpha(col, 0x66) : FIELD);
                    ctx.drawTextWithShadow(tr, v, right - bw + (bw - tr.getWidth(v)) / 2, r.y() + 7, Theme.TEXT);
                }
                case 2 -> {
                    BoolSetting b = (BoolSetting) r.s();
                    ctx.drawTextWithShadow(tr, b.name, sx + 12, r.y() + 7, Theme.TEXT);
                    Theme.pill(ctx, right - 20, r.y() + 6, b.value, col);
                }
                case 3 -> {
                    NumberSetting n = (NumberSetting) r.s();
                    ctx.drawTextWithShadow(tr, n.name, sx + 12, r.y() + 4, Theme.TEXT);
                    String v = format(n);
                    ctx.drawTextWithShadow(tr, v, right - tr.getWidth(v), r.y() + 4, Theme.TEXT_DIM);

                    int tx1 = sx + 12, tx2 = right, ty = r.y() + 20;
                    Theme.rounded(ctx, tx1, ty, tx2, ty + 4, 0xFF2A2A3A);
                    double t = (n.value - n.min) / (n.max - n.min);
                    int fx = tx1 + (int) ((tx2 - tx1) * t);
                    if (fx > tx1) Theme.hGradient(ctx, tx1, ty, fx, ty + 4, col, Theme.lerp(col, 0xFFFFFFFF, 0.35f));
                    Theme.rounded(ctx, fx - 3, ty - 2, fx + 3, ty + 6, 0xFFFFFFFF);
                }
                default -> {}
            }
        }
    }

    // ------------------------------------------------------------------ input

    private void updateSlider(double mx) {
        double t = Math.max(0, Math.min(1, (mx - sliderX) / sliderW));
        draggingSlider.set(draggingSlider.min + t * (draggingSlider.max - draggingSlider.min));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        if (listeningKey) { listeningKey = false; return true; }
        MinecraftClient mc = MinecraftClient.getInstance();

        // sidebar
        if (mx >= wx && mx < wx + SIDE) {
            int y = wy + TITLE + 10;
            for (Category c : Category.values()) {
                if (my >= y && my < y + 24) {
                    selCat = c; search = ""; scroll = 0; selMod = firstIn(c);
                    return true;
                }
                y += 26;
            }
        }

        // module list
        int lx = wx + SIDE + 1;
        if (mx >= lx && mx < lx + LIST - 1 && my > wy + TITLE) {
            List<Module> mods = visible();
            int idx = (int) Math.floor((my - listTop() + scroll) / ROW);
            if (idx >= 0 && idx < mods.size()) {
                Module m = mods.get(idx);
                int pillX = lx + LIST - 8 - 26;
                if (button == 0 && mx >= pillX - 2) m.toggle(mc);
                else if (button == 2) { selMod = m; listeningKey = true; }
                else selMod = m;
                return true;
            }
        }

        // settings
        int sx = sx(), sw = sw();
        if (mx >= sx && mx < sx + sw && selMod != null && button == 0) {
            for (SRow r : settingRows()) {
                if (my < r.y() || my >= r.y() + r.h()) continue;
                switch (r.kind()) {
                    case 0 -> selMod.toggle(mc);
                    case 1 -> listeningKey = true;
                    case 2 -> ((BoolSetting) r.s()).toggle();
                    case 3 -> {
                        draggingSlider = (NumberSetting) r.s();
                        sliderX = sx + 12;
                        sliderW = sw - 24;
                        updateSlider(mx);
                    }
                    default -> {}
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        double mx = click.x();
        if (draggingSlider != null) { updateSlider(mx); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingSlider = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx >= wx + SIDE && mx < wx + SIDE + LIST) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - v * ROW));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean charTyped(CharInput input) {
        int chr = input.codepoint();
        if (listeningKey) return true;
        if (chr >= 32 && search.length() < 18) {
            search += new String(Character.toChars(chr));
            scroll = 0;
            List<Module> v = visible();
            if (!v.isEmpty()) selMod = v.get(0);
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.getKeycode();
        if (listeningKey) {
            if (selMod != null) {
                selMod.key = (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE)
                        ? GLFW.GLFW_KEY_UNKNOWN : keyCode;
            }
            listeningKey = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            scroll = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !search.isEmpty()) { search = ""; return true; }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public void removed() { Config.save(); }
}

// ===== Config.java =====
class Config {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("bigback.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        JsonObject root = new JsonObject();
        JsonObject mods = new JsonObject();
        for (Module m : HackClient.MODULES) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.enabled);
            o.addProperty("key", m.key);
            JsonObject s = new JsonObject();
            for (Setting<?> st : m.settings) {
                if (st instanceof BoolSetting b) s.addProperty(b.name, b.value);
                else if (st instanceof NumberSetting n) s.addProperty(n.name, n.value);
            }
            o.add("settings", s);
            mods.add(m.name, o);
        }
        root.add("modules", mods);

        try { Files.writeString(FILE, GSON.toJson(root)); }
        catch (IOException ex) { System.err.println("[BigBack] Failed to save config: " + ex); }
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            JsonObject mods = root.getAsJsonObject("modules");
            if (mods != null) for (Module m : HackClient.MODULES) {
                if (!mods.has(m.name)) continue;
                JsonObject o = mods.getAsJsonObject(m.name);
                if (o.has("enabled")) m.enabled = o.get("enabled").getAsBoolean();
                if (o.has("key")) m.key = o.get("key").getAsInt();
                JsonObject s = o.getAsJsonObject("settings");
                if (s == null) continue;
                for (Setting<?> st : m.settings) {
                    if (!s.has(st.name)) continue;
                    if (st instanceof BoolSetting b) b.value = s.get(st.name).getAsBoolean();
                    else if (st instanceof NumberSetting n) n.set(s.get(st.name).getAsDouble());
                }
            }
        } catch (IOException | RuntimeException ex) {
            System.err.println("[BigBack] Failed to load config: " + ex);
        }
    }
}

// ===== Damage.java =====
/** Approximate vanilla explosion damage (no Blast Protection, so it slightly overestimates armored targets). */
class Damage {
    public static final float CRYSTAL_POWER = 6f, ANCHOR_POWER = 5f;

    public static float effectiveHealth(LivingEntity e) {
        return e.getHealth() + e.getAbsorptionAmount();
    }

    /** @param ignore a block that vanishes in the blast (the anchor itself), or null */
    public static float explosion(LivingEntity e, Vec3d center, float power, BlockPos ignore) {
        double q = 2.0 * power;
        double dist = e.getPos().distanceTo(center) / q;
        if (dist > 1.0) return 0f;

        double impact = (1.0 - dist) * exposure(e, center, ignore);
        float dmg = (float) ((impact * impact + impact) / 2.0 * 7.0 * q + 1.0);

        switch (MinecraftClient.getInstance().world.getDifficulty()) {
            case PEACEFUL -> { return 0f; }
            case EASY -> dmg = Math.min(dmg / 2f + 1f, dmg);
            case HARD -> dmg = dmg * 1.5f;
            default -> {}
        }

        float armor = e.getArmor();
        float toughness = (float) e.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        float f = 2f + toughness / 4f;
        float g = MathHelper.clamp(armor - dmg / f, armor * 0.2f, 20f);
        dmg *= 1f - g / 25f;

        var res = e.getStatusEffect(StatusEffects.RESISTANCE);
        if (res != null) dmg *= Math.max(0f, 1f - 0.2f * (res.getAmplifier() + 1));
        return Math.max(0f, dmg);
    }

    private static float exposure(Entity e, Vec3d center, BlockPos ignore) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Box b = e.getBoundingBox();
        int clear = 0, total = 0;
        for (int ix = 0; ix <= 2; ix++)
            for (int iy = 0; iy <= 2; iy++)
                for (int iz = 0; iz <= 2; iz++) {
                    Vec3d p = new Vec3d(MathHelper.lerp(ix / 2.0, b.minX, b.maxX),
                                        MathHelper.lerp(iy / 2.0, b.minY, b.maxY),
                                        MathHelper.lerp(iz / 2.0, b.minZ, b.maxZ));
                    total++;
                    HitResult r = mc.world.raycast(new RaycastContext(p, center,
                            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, e));
                    boolean blocked = r.getType() != HitResult.Type.MISS;
                    if (blocked && ignore != null && r instanceof BlockHitResult bhr
                            && bhr.getBlockPos().equals(ignore)) blocked = false;
                    if (!blocked) clear++;
                }
        return clear / (float) total;
    }
}

// ===== ElytraBoost.java =====
/** Automates the vanilla firework boost while gliding: handy for long-distance travel. */
class ElytraBoost extends Module {
    private final NumberSetting minSpeed = number("Min Speed (b/s)", 30, 10, 60, 1);
    private final NumberSetting delay = number("Delay (ticks)", 30, 10, 100, 5);
    private int timer;

    public ElytraBoost(int key) { super("ElytraBoost", Category.MOVEMENT, key); }

    /** Pose is named FALL_FLYING or GLIDING depending on mappings; compare by name to stay version-proof. */
    private static boolean gliding(MinecraftClient mc) {
        String n = mc.player.getPose().name();
        return n.equals("GLIDING") || n.equals("FALL_FLYING");
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (timer-- > 0) return;
        if (!gliding(mc)) return;
        if (mc.player.getVelocity().length() * 20 >= minSpeed.value) return;

        int slot = Util.findHotbar(mc, Items.FIREWORK_ROCKET);
        if (slot == -1) return;
        int old = Util.selected(mc);
        Util.select(mc, slot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        Util.select(mc, old);
        timer = delay.intValue();
    }
}

// ===== Finds.java =====
final class Find {
    final String type, dim;
    final int x, y, z, score;
    Find(String type, String dim, int x, int y, int z, int score) {
        this.type = type; this.dim = dim; this.x = x; this.y = y; this.z = z; this.score = score;
    }
}

/** Per-server list of base-finder results, saved to config/bigback_finds_<server>.json. */
final class Finds {
    static final List<Find> LIST = new ArrayList<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static String key = "none";

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bigback_finds_" + key + ".json");
    }

    /** Call when the world changes (join, dimension switch). */
    static void switchTo(MinecraftClient mc) {
        key = mc.getCurrentServerEntry() != null
                ? mc.getCurrentServerEntry().address.replaceAll("[^a-zA-Z0-9.\\-]", "_")
                : "singleplayer";
        LIST.clear();
        try {
            Path f = file();
            if (Files.exists(f)) {
                List<Find> l = GSON.fromJson(Files.readString(f), new TypeToken<List<Find>>() {}.getType());
                if (l != null) LIST.addAll(l);
            }
        } catch (Exception ignored) {}
    }

    static String dim(MinecraftClient mc) { return mc.world.getRegistryKey().getValue().toString(); }

    /** @return true if this is a new find (not within 48 blocks of one of the same type). */
    static boolean add(MinecraftClient mc, String type, BlockPos p, int score) {
        String dim = dim(mc);
        for (Find f : LIST)
            if (f.type.equals(type) && f.dim.equals(dim)
                    && Math.abs(f.x - p.getX()) < 48 && Math.abs(f.z - p.getZ()) < 48) return false;
        LIST.add(new Find(type, dim, p.getX(), p.getY(), p.getZ(), score));
        save();
        return true;
    }

    static void clear() { LIST.clear(); save(); }

    static void save() {
        try { Files.writeString(file(), GSON.toJson(LIST)); } catch (IOException ignored) {}
    }
}

// ===== FindsHud.java =====
/** Shows the nearest base-finder results on the HUD. */
class FindsHud extends Module {
    final NumberSetting maxShown = number("Max Shown", 5, 1, 10, 1);
    private final BoolSetting clear = bool("Clear Finds (click)", false);

    public FindsHud() {
        super("Finds List", Category.WORLD, GLFW.GLFW_KEY_UNKNOWN);
        enabled = true;
        hidden = true;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (clear.value) { Finds.clear(); clear.value = false; }
    }
}

// ===== FrameFinder.java =====
/** Item frames and armor stands in loaded entity range (about 64 blocks) are a strong sign of a base. */
class FrameFinder extends Module {
    private final NumberSetting threshold = number("Threshold", 4, 1, 30, 1);
    private final BoolSetting chat = bool("Chat Alerts", true);
    private int timer;

    public FrameFinder() { super("FrameFinder", Category.WORLD, -1); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || timer-- > 0) return;
        timer = 40;

        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, BlockPos> where = new HashMap<>();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemFrameEntity || e instanceof ArmorStandEntity)) continue;
            long k = ChunkPos.toLong(e.getBlockPos());
            counts.merge(k, 1, Integer::sum);
            where.putIfAbsent(k, e.getBlockPos());
        }
        for (var en : counts.entrySet()) {
            if (en.getValue() < threshold.intValue()) continue;
            BlockPos p = where.get(en.getKey());
            if (Finds.add(mc, "Frames", p, en.getValue()) && chat.value) {
                mc.player.sendMessage(Text.literal("\u00a7d[BigBack] \u00a7fFrames \u00a77(" + en.getValue()
                        + ") \u00a7fat \u00a7b" + p.getX() + " " + p.getY() + " " + p.getZ()), false);
            }
        }
    }
}

// ===== Hud.java =====
class Hud {
    public static void init() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("bigback", "hud"), Hud::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen instanceof ClickGUI) return;

        int infoY = 22;
        if (HackClient.WATERMARK.enabled) watermark(ctx, mc);
        if (HackClient.INFO.enabled) infoY = info(ctx, mc, infoY);
        if (HackClient.FINDS.enabled) finds(ctx, mc, infoY + 4);
        if (HackClient.ARMOR.enabled) armor(ctx, mc);
        arraylist(ctx, mc);
    }

    private static void watermark(DrawContext ctx, MinecraftClient mc) {
        var tr = mc.textRenderer;
        Watermark wm = HackClient.WATERMARK;
        String big = "BigBack", rest = " Client";
        String extra = wm.fps.value ? "  |  " + mc.getCurrentFps() + " FPS" : "";

        int x = 4, y = 4;
        int w = 12 + tr.getWidth(big) + tr.getWidth(rest) + tr.getWidth(extra);
        int c1 = wm.animated.value ? Theme.animated(0f) : Theme.brand(0f);
        int c2 = wm.animated.value ? Theme.animated(0.35f) : Theme.brand(1f);

        ctx.fill(x, y, x + w, y + 16, 0xB0121218);
        Theme.hGradient(ctx, x, y, x + w, y + 2, c1, c2);

        int tx = x + 6;
        if (wm.animated.value) tx = Theme.gradientText(ctx, tr, big, tx, y + 5, 0f);
        else {
            for (int i = 0; i < big.length(); i++) {
                String ch = String.valueOf(big.charAt(i));
                ctx.drawTextWithShadow(tr, ch, tx, y + 5, Theme.brand(i / (float) big.length()));
                tx += tr.getWidth(ch);
            }
        }
        ctx.drawTextWithShadow(tr, rest, tx, y + 5, Theme.TEXT);
        tx += tr.getWidth(rest);
        if (!extra.isEmpty()) ctx.drawTextWithShadow(tr, extra, tx, y + 5, Theme.TEXT_DIM);
    }

    private static int info(DrawContext ctx, MinecraftClient mc, int y) {
        var tr = mc.textRenderer;
        InfoHud info = HackClient.INFO;
        List<String> lines = new ArrayList<>();
        if (info.coords.value)
            lines.add(String.format("XYZ  %.1f / %.1f / %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        if (info.facing.value) {
            String f = mc.player.getHorizontalFacing().asString();
            lines.add("Facing  " + Character.toUpperCase(f.charAt(0)) + f.substring(1));
        }
        if (info.dimension.value) {
            String d = Finds.dim(mc);
            lines.add("Dimension  " + d.substring(d.indexOf(':') + 1));
        }
        if (info.speed.value) {
            Vec3d v = mc.player.getVelocity();
            lines.add(String.format("Speed  %.1f b/s", Math.hypot(v.x, v.z) * 20));
        }
        if (info.ping.value && mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (e != null) lines.add("Ping  " + e.getLatency() + " ms");
        }
        for (String line : lines) {
            ctx.drawTextWithShadow(tr, line, 6, y, Theme.TEXT);
            y += 10;
        }
        return y;
    }

    private static void finds(DrawContext ctx, MinecraftClient mc, int y) {
        var tr = mc.textRenderer;
        String dim = Finds.dim(mc);
        double px = mc.player.getX(), pz = mc.player.getZ();
        List<Find> list = new ArrayList<>();
        for (Find f : Finds.LIST) if (f.dim.equals(dim)) list.add(f);
        if (list.isEmpty()) return;
        list.sort(Comparator.comparingDouble(f -> Math.hypot(f.x - px, f.z - pz)));

        ctx.drawTextWithShadow(tr, "Finds", 6, y, Theme.category(Category.WORLD));
        y += 11;
        int shown = HackClient.FINDS.maxShown.intValue();
        for (int i = 0; i < Math.min(shown, list.size()); i++) {
            Find f = list.get(i);
            int dist = (int) Math.hypot(f.x - px, f.z - pz);
            String line = f.type + "  " + f.x + " " + f.y + " " + f.z + "  (" + dist + "m)";
            ctx.drawTextWithShadow(tr, line, 6, y, Theme.TEXT);
            y += 10;
        }
    }

    private static void armor(DrawContext ctx, MinecraftClient mc) {
        var tr = mc.textRenderer;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        int totems = 0;
        if (HackClient.ARMOR.totems.value) {
            for (int i = 0; i < 36; i++) {
                ItemStack st = mc.player.getInventory().getStack(i);
                if (st.isOf(Items.TOTEM_OF_UNDYING)) totems += st.getCount();
            }
            if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) totems += mc.player.getOffHandStack().getCount();
        }
        int n = 4 + (totems > 0 ? 1 : 0);
        int x = ctx.getScaledWindowWidth() - 8 - n * 18, y = ctx.getScaledWindowHeight() - 26;
        ctx.fill(x - 4, y - 4, x + n * 18 + 1, y + 20, 0x90121218);
        Theme.hGradient(ctx, x - 4, y - 4, x + n * 18 + 1, y - 2, Theme.animated(0f), Theme.animated(0.4f));
        for (EquipmentSlot slot : slots) {
            ItemStack st = mc.player.getEquippedStack(slot);
            if (!st.isEmpty()) { ctx.drawItem(st, x, y); ctx.drawStackOverlay(tr, st, x, y); }
            x += 18;
        }
        if (totems > 0) {
            ItemStack t = new ItemStack(Items.TOTEM_OF_UNDYING, totems);
            ctx.drawItem(t, x, y);
            ctx.drawStackOverlay(tr, t, x, y);
        }
    }

    private static void arraylist(DrawContext ctx, MinecraftClient mc) {
        var tr = mc.textRenderer;
        List<Module> active = HackClient.MODULES.stream()
                .filter(m -> m.enabled && !m.hidden)
                .sorted(Comparator.comparingInt((Module m) -> tr.getWidth(m.name)).reversed())
                .toList();

        int screenW = ctx.getScaledWindowWidth();
        int y = 4, i = 0;
        for (Module m : active) {
            int color = Theme.animated(i * 0.07f);
            int x = screenW - tr.getWidth(m.name) - 7;
            ctx.fill(x - 3, y - 1, screenW, y + 10, 0xA0121218);
            ctx.fill(screenW - 2, y - 1, screenW, y + 10, color);
            ctx.drawTextWithShadow(tr, m.name, x, y + 1, color);
            y += 11;
            i++;
        }
    }
}

// ===== InfoHud.java =====
class InfoHud extends Module {
    final BoolSetting coords = bool("Coords", true);
    final BoolSetting facing = bool("Facing", true);
    final BoolSetting speed = bool("Speed", true);
    final BoolSetting ping = bool("Ping", true);
    final BoolSetting dimension = bool("Dimension", false);

    public InfoHud() {
        super("Info", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
        enabled = true;
        hidden = true;
    }
}

// ===== KillAura.java =====
class KillAura extends Module {
    private final NumberSetting range = number("Range", 3.5, 1, 6, 0.1);
    private final NumberSetting cooldown = number("Cooldown", 0.95, 0.5, 1.0, 0.05);

    public KillAura(int key) { super("KillAura", Category.COMBAT, key); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;
        PlayerEntity t = Util.nearestPlayer(mc, range.value);
        if (t == null) return;
        if (mc.player.getAttackCooldownProgress(0.5f) < cooldown.value) return;
        mc.interactionManager.attackEntity(mc.player, t);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}

// ===== Module.java =====
abstract class Module {
    public final String name;
    public final Category category;
    final List<Setting<?>> settings = new ArrayList<>();
    public boolean enabled;
    public boolean expanded;
    public boolean hidden; // true = never listed in the HUD arraylist
    public int key;        // GLFW key code, -1 = unbound
    private boolean wasDown;

    public Module(String name, Category category, int defaultKey) {
        this.name = name;
        this.category = category;
        this.key = defaultKey;
    }

    protected BoolSetting bool(String name, boolean def) {
        BoolSetting s = new BoolSetting(name, def);
        settings.add(s);
        return s;
    }

    protected NumberSetting number(String name, double def, double min, double max, double step) {
        NumberSetting s = new NumberSetting(name, def, min, max, step);
        settings.add(s);
        return s;
    }

    public void toggle(MinecraftClient mc) {
        enabled = !enabled;
        if (!enabled) onDisable(mc);
        if (mc.player != null && !hidden)
            mc.player.sendMessage(Text.literal(name + (enabled ? " \u00a7aON" : " \u00a7cOFF")), true);
    }

    public void handleKey(MinecraftClient mc) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) { wasDown = false; return; }
        boolean down = GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
        if (down && !wasDown && mc.currentScreen == null) toggle(mc);
        wasDown = down;
    }

    public void onTick(MinecraftClient mc) {}
    public void onDisable(MinecraftClient mc) {}
}

// ===== NoFall.java =====
class NoFall extends Module {
    private final NumberSetting distance = number("Fall Distance", 2.5, 1, 10, 0.5);

    public NoFall(int key) { super("NoFall", Category.MOVEMENT, key); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (mc.player.fallDistance > distance.value && !mc.player.isOnGround()) {
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
        }
    }
}

// ===== Parkour.java =====
/** Jumps automatically when you reach the edge of a block. */
class Parkour extends Module {
    public Parkour(int key) { super("Parkour", Category.MOVEMENT, key); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isOnGround() || mc.player.isSneaking()) return;
        Box below = mc.player.getBoundingBox().expand(-0.1, 0, -0.1).offset(0, -0.5, 0);
        if (mc.world.isSpaceEmpty(mc.player, below)) mc.player.jump();
    }
}

// ===== PortalFinder.java =====
/** Lit nether portals are player-made, so they point at a base. */
class PortalFinder extends ScannerModule {
    public PortalFinder() { super("PortalFinder", "Portal", 2, 20); }

    @Override
    protected int score(WorldChunk chunk, MinecraftClient mc) {
        int count = 0;
        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty() || !sec.hasAny(st -> st.isOf(Blocks.NETHER_PORTAL))) continue;
            int baseY = (chunk.getBottomSectionCoord() + i) << 4;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                if (sec.getBlockState(x, y, z).isOf(Blocks.NETHER_PORTAL)) {
                    count++;
                    if (rep == null) rep = new BlockPos(chunk.getPos().getStartX() + x, baseY + y, chunk.getPos().getStartZ() + z);
                }
            }
        }
        return count;
    }
}

// ===== ScannerModule.java =====
/**
 * Base for passive base finders. They only read chunk data the server already sent you
 * (block entities, palettes) and send nothing back, so there is no network or movement
 * footprint for an anticheat to look at.
 */
abstract class ScannerModule extends Module {
    protected final NumberSetting perTick;
    protected final NumberSetting threshold;
    protected final BoolSetting chat;
    protected BlockPos rep; // representative position set by score()

    private final String label;
    private final Set<Long> scanned = new HashSet<>();
    private Object lastWorld;

    ScannerModule(String name, String label, int defThreshold, int maxThreshold) {
        super(name, Category.WORLD, GLFW.GLFW_KEY_UNKNOWN);
        this.label = label;
        this.threshold = number("Threshold", defThreshold, 1, maxThreshold, 1);
        this.perTick = number("Chunks/Tick", 1, 1, 4, 1);
        this.chat = bool("Chat Alerts", true);
    }

    /** Return how "base-like" a chunk is; set {@link #rep} to a position worth showing. */
    protected abstract int score(WorldChunk chunk, MinecraftClient mc);

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (mc.world != lastWorld) { scanned.clear(); lastWorld = mc.world; }

        int r = mc.options.getViewDistance().getValue();
        ChunkPos pc = mc.player.getChunkPos();
        int budget = perTick.intValue();

        for (int dx = -r; dx <= r && budget > 0; dx++) {
            for (int dz = -r; dz <= r && budget > 0; dz++) {
                int cx = pc.x + dx, cz = pc.z + dz;
                long key = ChunkPos.toLong(cx, cz);
                if (scanned.contains(key)) continue;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                scanned.add(key);
                budget--;
                rep = null;
                int s = score(chunk, mc);
                if (rep != null && s >= threshold.intValue() && Finds.add(mc, label, rep, s) && chat.value) {
                    mc.player.sendMessage(Text.literal("\u00a7d[BigBack] \u00a7f" + label + " \u00a77(score " + s
                            + ") \u00a7fat \u00a7b" + rep.getX() + " " + rep.getY() + " " + rep.getZ()), false);
                }
            }
        }
    }

    @Override
    public void onDisable(MinecraftClient mc) { scanned.clear(); }
}

// ===== Settings.java =====
abstract class Setting<T> {
    final String name;
    T value;
    Setting(String name, T value) { this.name = name; this.value = value; }
}

class BoolSetting extends Setting<Boolean> {
    BoolSetting(String name, boolean def) { super(name, def); }
    void toggle() { value = !value; }
}

class NumberSetting extends Setting<Double> {
    final double min, max, step;
    NumberSetting(String name, double def, double min, double max, double step) {
        super(name, def);
        this.min = min; this.max = max; this.step = step;
    }
    void set(double v) {
        v = Math.round(v / step) * step;
        value = Math.max(min, Math.min(max, v));
    }
    int intValue() { return (int) Math.round(value); }
}

// ===== SignFinder.java =====
class SignFinder extends ScannerModule {
    public SignFinder() { super("SignFinder", "Signs", 3, 20); }

    @Override
    protected int score(WorldChunk chunk, MinecraftClient mc) {
        int s = 0;
        for (BlockEntity be : chunk.getBlockEntities().values())
            if (be instanceof SignBlockEntity) { s++; rep = be.getPos(); }
        return s;
    }
}

// ===== SpawnerFinder.java =====
class SpawnerFinder extends ScannerModule {
    public SpawnerFinder() { super("SpawnerFinder", "Spawner", 1, 10); }

    @Override
    protected int score(WorldChunk chunk, MinecraftClient mc) {
        int s = 0;
        for (BlockEntity be : chunk.getBlockEntities().values())
            if (be instanceof MobSpawnerBlockEntity) { s++; rep = be.getPos(); }
        return s;
    }
}

// ===== Speed.java =====
class Speed extends Module {
    // capped at 1.8: ground friction is ~0.55, so anything higher would compound forever
    private final NumberSetting multiplier = number("Multiplier", 1.3, 1.0, 1.8, 0.05);
    private final BoolSetting autoJump = bool("Auto Jump", false);

    public Speed(int key) { super("Speed", Category.MOVEMENT, key); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;
        var in = mc.player.input.playerInput;
        boolean moving = in.forward() || in.backward() || in.left() || in.right();
        if (!moving || !mc.player.isOnGround()) return;
        if (autoJump.value) mc.player.jump();
        Vec3d v = mc.player.getVelocity();
        mc.player.setVelocity(v.x * multiplier.value, v.y, v.z * multiplier.value);
    }
}

// ===== Step.java =====
class Step extends Module {
    private final NumberSetting height = number("Height", 1.0, 0.6, 2.5, 0.1);

    public Step(int key) { super("Step", Category.MOVEMENT, key); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;
        var attr = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (attr != null) attr.setBaseValue(height.value);
    }

    @Override
    public void onDisable(MinecraftClient mc) {
        if (mc.player == null) return;
        var attr = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (attr != null) attr.setBaseValue(0.6);
    }
}

// ===== StorageFinder.java =====
/** Flags chunks with lots of chests, barrels, shulkers and hoppers. Natural structures (villages, mineshafts) cause some false positives; raise the threshold to filter them. */
class StorageFinder extends ScannerModule {
    public StorageFinder() { super("StorageFinder", "Storage", 8, 60); }

    @Override
    protected int score(WorldChunk chunk, MinecraftClient mc) {
        int s = 0;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            int w = 0;
            if (be instanceof ShulkerBoxBlockEntity) w = 3;
            else if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                    || be instanceof EnderChestBlockEntity || be instanceof HopperBlockEntity) w = 1;
            if (w > 0) { s += w; rep = be.getPos(); }
        }
        return s;
    }
}

// ===== Theme.java =====
/** BigBack look: dark neutral surfaces, one accent colour per category, violet -> sky -> mint brand gradient. */
final class Theme {
    public static final int PANEL_BG = 0xE61A1A24;
    public static final int SETTING_BG = 0x66000000;
    public static final int HOVER = 0x22FFFFFF;
    public static final int TEXT = 0xFFF1F1F6;
    public static final int TEXT_DIM = 0xFF8E8EA3;
    public static final int OFF_PILL = 0xFF3A3A4A;

    private static final int[] BRAND = {0xFF8B5CF6, 0xFF38BDF8, 0xFF34D399};

    private Theme() {}

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = a >>> 24, rr = (a >> 16) & 255, gr = (a >> 8) & 255, br = a & 255;
        int ab = b >>> 24, rb = (b >> 16) & 255, gb = (b >> 8) & 255, bb = b & 255;
        return ((int) (ar + (ab - ar) * t) << 24) | ((int) (rr + (rb - rr) * t) << 16)
             | ((int) (gr + (gb - gr) * t) << 8) | (int) (br + (bb - br) * t);
    }

    public static int alpha(int color, int a) { return (a << 24) | (color & 0xFFFFFF); }

    /** Colour at position t (0..1) on the brand gradient. */
    public static int brand(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float s = t * (BRAND.length - 1);
        int i = Math.min((int) s, BRAND.length - 2);
        return lerp(BRAND[i], BRAND[i + 1], s - i);
    }

    /** Slowly drifting brand colour; offset staggers elements so the gradient appears to flow. */
    public static int animated(float offset) {
        float t = (((System.currentTimeMillis() % 5000L) / 5000f) + offset) % 1f;
        if (t < 0) t += 1f;
        return brand(t < 0.5f ? t * 2f : (1f - t) * 2f);
    }

    public static int category(Category c) {
        return switch (c) {
            case COMBAT -> 0xFFFB7185;   // rose
            case MOVEMENT -> 0xFF38BDF8; // sky
            case PLAYER -> 0xFF34D399;   // mint
            case WORLD -> 0xFFFBBF24;    // amber
            case RENDER -> 0xFFA78BFA;   // violet
        };
    }

    public static void hGradient(DrawContext ctx, int x1, int y1, int x2, int y2, int c1, int c2) {
        int w = x2 - x1;
        for (int i = 0; i < w; i += 2) {
            ctx.fill(x1 + i, y1, Math.min(x1 + i + 2, x2), y2, lerp(c1, c2, i / (float) Math.max(1, w - 1)));
        }
    }

    /** Draws text with a per-letter animated gradient; returns the x after the last letter. */
    public static int gradientText(DrawContext ctx, TextRenderer tr, String s, int x, int y, float phase) {
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            ctx.drawTextWithShadow(tr, ch, x, y, animated(phase + i * 0.05f));
            x += tr.getWidth(ch);
        }
        return x;
    }

    /** Rect with softened corners (cuts the corner pixels). */
    public static void rounded(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1 + 1, y1, x2 - 1, y2, color);
        ctx.fill(x1, y1 + 1, x2, y2 - 1, color);
    }

    public static void pill(DrawContext ctx, int x, int y, boolean on, int accent) {
        rounded(ctx, x, y, x + 20, y + 10, on ? accent : OFF_PILL);
        int k = on ? x + 11 : x + 1;
        rounded(ctx, k, y + 1, k + 8, y + 9, 0xFFFFFFFF);
    }
}

// ===== TriggerBot.java =====
class TriggerBot extends Module {
    private final NumberSetting cooldown = number("Cooldown", 0.95, 0.5, 1.0, 0.05);
    private final BoolSetting playersOnly = bool("Players Only", true);

    public TriggerBot(int key) { super("TriggerBot", Category.COMBAT, key); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity target) || !target.isAlive()) return;
        if (playersOnly.value && !(target instanceof PlayerEntity)) return;
        if (mc.player.getAttackCooldownProgress(0.5f) < cooldown.value) return;
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}

// ===== Util.java =====
class Util {
    public static int findHotbar(MinecraftClient mc, Item item) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    public static int findInventory(MinecraftClient mc, Item item) {
        for (int i = 0; i < 36; i++)
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    public static void select(MinecraftClient mc, int slot) {
        if (slot >= 0 && slot < 9) mc.player.getInventory().setSelectedSlot(slot);
    }

    public static int selected(MinecraftClient mc) {
        return mc.player.getInventory().getSelectedSlot();
    }

    public static PlayerEntity nearestPlayer(MinecraftClient mc, double range) {
        PlayerEntity best = null;
        double bestDist = range * range;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isDead()) continue;
            double d = mc.player.squaredDistanceTo(p);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }
}

// ===== UtilityFinder.java =====
/** Furnaces, brewing stands, enchanting tables and beacons (beacons count heavily). */
class UtilityFinder extends ScannerModule {
    public UtilityFinder() { super("UtilityFinder", "Utility", 6, 40); }

    @Override
    protected int score(WorldChunk chunk, MinecraftClient mc) {
        int s = 0;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            int w = 0;
            if (be instanceof BeaconBlockEntity) w = 6;
            else if (be instanceof EnchantingTableBlockEntity) w = 2;
            else if (be instanceof AbstractFurnaceBlockEntity || be instanceof BrewingStandBlockEntity) w = 1;
            if (w > 0) { s += w; rep = be.getPos(); }
        }
        return s;
    }
}

// ===== Watermark.java =====
class Watermark extends Module {
    final BoolSetting animated = bool("Animated", true);
    final BoolSetting fps = bool("Show FPS", true);

    public Watermark() {
        super("Watermark", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
        enabled = true;
        hidden = true;
    }
}

// ===== Zoom.java =====
class Zoom extends Module {
    private final NumberSetting fov = number("FOV", 30, 30, 90, 5);
    private int prev = -1;

    public Zoom(int key) { super("Zoom", Category.RENDER, key); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.options == null) return;
        if (prev == -1) prev = mc.options.getFov().getValue();
        mc.options.getFov().setValue(fov.intValue());
    }

    @Override
    public void onDisable(MinecraftClient mc) {
        if (prev != -1) mc.options.getFov().setValue(prev);
        prev = -1;
    }
}
