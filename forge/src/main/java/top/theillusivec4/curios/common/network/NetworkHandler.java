/*
 * Copyright (c) 2018-2024 C4
 *
 * This file is part of Curios, a mod made for Minecraft.
 *
 * Curios is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Curios is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Curios.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package top.theillusivec4.curios.common.network;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
import top.theillusivec4.curios.CuriosConstants;
import top.theillusivec4.curios.common.network.client.CPacketDestroy;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;
import top.theillusivec4.curios.common.network.client.CPacketOpenVanilla;
import top.theillusivec4.curios.common.network.client.CPacketPage;
import top.theillusivec4.curios.common.network.client.CPacketToggleCosmetics;
import top.theillusivec4.curios.common.network.client.CPacketToggleRender;
import top.theillusivec4.curios.common.network.server.SPacketBreak;
import top.theillusivec4.curios.common.network.server.SPacketGrabbedItem;
import top.theillusivec4.curios.common.network.server.SPacketPage;
import top.theillusivec4.curios.common.network.server.SPacketQuickMove;
import top.theillusivec4.curios.common.network.server.SPacketSetIcons;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncCurios;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncData;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncModifiers;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncRender;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncStack;

public class NetworkHandler {

  private static final int PTC_VERSION = 1;

  public static SimpleChannel INSTANCE;
  public static RegistryAccess REGISTRY_ACCESS;

  public static void register() {

    INSTANCE = ChannelBuilder.named(new ResourceLocation(CuriosConstants.MOD_ID, "main"))
        .networkProtocolVersion(PTC_VERSION)
        .clientAcceptedVersions(Channel.VersionTest.exact(PTC_VERSION))
        .serverAcceptedVersions(Channel.VersionTest.exact(PTC_VERSION)).simpleChannel();

    //Client Packets
    register(CPacketOpenCurios.class, CPacketOpenCurios::encode, CPacketOpenCurios::decode,
        CPacketOpenCurios::handle);
    register(CPacketOpenVanilla.class, CPacketOpenVanilla::encode, CPacketOpenVanilla::decode,
        CPacketOpenVanilla::handle);
    register(CPacketDestroy.class, CPacketDestroy::encode, CPacketDestroy::decode,
        CPacketDestroy::handle);
    register(CPacketToggleRender.class, CPacketToggleRender::encode, CPacketToggleRender::decode,
        CPacketToggleRender::handle);
    register(CPacketPage.class, CPacketPage::encode, CPacketPage::decode, CPacketPage::handle);
    register(CPacketToggleCosmetics.class, CPacketToggleCosmetics::encode,
        CPacketToggleCosmetics::decode, CPacketToggleCosmetics::handle);

    // Server Packets
    register(SPacketSyncStack.class, SPacketSyncStack::encode, SPacketSyncStack::decode,
        SPacketSyncStack::handle);
    register(SPacketSyncCurios.class, SPacketSyncCurios::encode, SPacketSyncCurios::decode,
        SPacketSyncCurios::handle);
    register(SPacketBreak.class, SPacketBreak::encode, SPacketBreak::decode, SPacketBreak::handle);
    register(SPacketGrabbedItem.class, SPacketGrabbedItem::encode, SPacketGrabbedItem::decode,
        SPacketGrabbedItem::handle);
    register(SPacketSetIcons.class, SPacketSetIcons::encode, SPacketSetIcons::decode,
        SPacketSetIcons::handle);
    register(SPacketSyncRender.class, SPacketSyncRender::encode, SPacketSyncRender::decode,
        SPacketSyncRender::handle);
    register(SPacketSyncModifiers.class, SPacketSyncModifiers::encode, SPacketSyncModifiers::decode,
        SPacketSyncModifiers::handle);
    register(SPacketSyncData.class, SPacketSyncData::encode, SPacketSyncData::decode,
        SPacketSyncData::handle);
    register(SPacketPage.class, SPacketPage::encode, SPacketPage::decode, SPacketPage::handle);
    register(SPacketQuickMove.class, SPacketQuickMove::encode, SPacketQuickMove::decode,
        SPacketQuickMove::handle);
  }

  private static <M> void register(Class<M> messageType, BiConsumer<M, FriendlyByteBuf> encoder,
                                   Function<FriendlyByteBuf, M> decoder,
                                   BiConsumer<M, CustomPayloadEvent.Context> messageConsumer) {
    INSTANCE.messageBuilder(messageType)
        .decoder(decoder)
        .encoder(encoder)
        .consumerNetworkThread(messageConsumer)
        .add();
  }
}
