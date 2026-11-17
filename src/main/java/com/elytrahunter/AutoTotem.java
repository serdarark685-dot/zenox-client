package com.elytrahunter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Auto Inventory Totem: the moment the offhand totem is gone (it popped), open the inventory
 * and swap a fresh totem into the offhand. Delay is adjustable (0 = as fast as the client can notice).
 */
final class AutoTotem {

    private static boolean hadTotem;
    private static int wait = -1;

    private AutoTotem() {}

    static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (!HunterConfig.totemEnabled || p == null || mc.world == null || mc.interactionManager == null) {
            hadTotem = false;
            wait = -1;
            return;
        }

        boolean has = p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        boolean popped = hadTotem && !has;
        hadTotem = has;

        if (wait < 0) {
            if (popped) {
                wait = HunterConfig.totemDelay;
            } else if (!HunterConfig.totemOnlyOnPop && p.getOffHandStack().isEmpty()) {
                wait = HunterConfig.totemDelay;
            } else {
                return;
            }
        }
        if (wait > 0) {
            wait--;
            return;
        }

        wait = -1;
        // another container (chest, ...) is open: leave it alone
        if (p.currentScreenHandler != p.playerScreenHandler) return;

        int slot = findTotemSlot(p);
        if (slot < 0) return;

        boolean opened = false;
        if (HunterConfig.totemOpenInventory && mc.currentScreen == null) {
            mc.setScreen(new InventoryScreen(p));
            opened = true;
        }
        // button 40 = swap with the offhand
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, p);
        if (opened) {
            p.closeHandledScreen();
        }
    }

    /** Container slot id of a totem: main inventory first, then the hotbar. -1 if none. */
    private static int findTotemSlot(ClientPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 9; i < 36; i++) {
            if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return 36 + i;
        }
        return -1;
    }
}
