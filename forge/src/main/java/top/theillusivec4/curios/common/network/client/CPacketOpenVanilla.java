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

package top.theillusivec4.curios.common.network.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;
import top.theillusivec4.curios.common.network.NetworkHandler;
import top.theillusivec4.curios.common.network.server.SPacketGrabbedItem;

public class CPacketOpenVanilla {

  private final ItemStack carried;

  public CPacketOpenVanilla(ItemStack stack) {
    this.carried = stack;
  }

  public static void encode(CPacketOpenVanilla msg, FriendlyByteBuf buf) {
    ItemStack.OPTIONAL_STREAM_CODEC.encode(
        RegistryFriendlyByteBuf.decorator(NetworkHandler.REGISTRY_ACCESS).apply(buf), msg.carried);
  }

  public static CPacketOpenVanilla decode(FriendlyByteBuf buf) {
    return new CPacketOpenVanilla(ItemStack.OPTIONAL_STREAM_CODEC.decode(
        RegistryFriendlyByteBuf.decorator(NetworkHandler.REGISTRY_ACCESS).apply(buf)));
  }

  public static void handle(CPacketOpenVanilla msg, CustomPayloadEvent.Context ctx) {
    ctx.enqueueWork(() -> {
      ServerPlayer sender = ctx.getSender();

      if (sender != null) {
        ItemStack stack = sender.isCreative() ? msg.carried : sender.containerMenu.getCarried();
        sender.containerMenu.setCarried(ItemStack.EMPTY);
        sender.doCloseContainer();

        if (!stack.isEmpty()) {

          if (!sender.isCreative()) {
            sender.containerMenu.setCarried(stack);
          }
          NetworkHandler.INSTANCE.send(new SPacketGrabbedItem(stack),
              PacketDistributor.PLAYER.with(sender));
        }
      }
    });
    ctx.setPacketHandled(true);
  }
}
