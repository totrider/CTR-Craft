package com.ctrcraft.kart;

import net.minecraft.world.item.Item;

/**
 * The Wrench: right-click a kart with it to change the kart's settings. For now one setting, whether driving this kart into a Start node's
 * area can start a race (on by default, so a kart you only want to drive round freely can be switched off). The kart's tuning profile
 * is meant to live here too later, see docs/DESIGN_KART_DATA.md. The behaviour is in {@link KartEntity#interact}.
 */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }
}
