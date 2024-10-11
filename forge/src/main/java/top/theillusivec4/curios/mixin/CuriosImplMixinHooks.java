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

package top.theillusivec4.curios.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CurioAttributeModifiers;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotAttribute;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.common.CuriosRegistry;
import top.theillusivec4.curios.common.capability.ItemizedCurioCapability;
import top.theillusivec4.curios.common.data.CuriosEntityManager;
import top.theillusivec4.curios.common.data.CuriosSlotManager;
import top.theillusivec4.curios.common.network.NetworkHandler;
import top.theillusivec4.curios.common.network.server.SPacketBreak;

public class CuriosImplMixinHooks {

  private static final Map<Item, ICurioItem> REGISTRY = new ConcurrentHashMap<>();

  public static void registerCurio(Item item, ICurioItem icurio) {
    REGISTRY.put(item, icurio);
  }

  public static Optional<ICurioItem> getCurioFromRegistry(Item item) {
    return Optional.ofNullable(REGISTRY.get(item));
  }

  public static Map<String, ISlotType> getSlots(boolean isClient) {
    CuriosSlotManager slotManager = isClient ? CuriosSlotManager.CLIENT : CuriosSlotManager.SERVER;
    return slotManager.getSlots();
  }

  public static Map<String, ISlotType> getEntitySlots(EntityType<?> type, boolean isClient) {
    CuriosEntityManager entityManager =
        isClient ? CuriosEntityManager.CLIENT : CuriosEntityManager.SERVER;
    return entityManager.getEntitySlots(type);
  }

  public static Map<String, ISlotType> getItemStackSlots(ItemStack stack, boolean isClient) {
    return filteredSlots(slotType -> {
      SlotContext slotContext = new SlotContext(slotType.getIdentifier(), null, 0, false, true);
      SlotResult slotResult = new SlotResult(slotContext, stack);
      return CuriosApi.testCurioPredicates(slotType.getValidators(), slotResult);
    }, CuriosApi.getSlots(isClient));
  }

  public static Map<String, ISlotType> getItemStackSlots(ItemStack stack,
                                                         LivingEntity livingEntity) {
    return filteredSlots(slotType -> {
      SlotContext slotContext =
          new SlotContext(slotType.getIdentifier(), livingEntity, 0, false, true);
      SlotResult slotResult = new SlotResult(slotContext, stack);
      return CuriosApi.testCurioPredicates(slotType.getValidators(), slotResult);
    }, CuriosApi.getEntitySlots(livingEntity));
  }

  private static Map<String, ISlotType> filteredSlots(Predicate<ISlotType> filter,
                                                      Map<String, ISlotType> map) {
    Map<String, ISlotType> result = new HashMap<>();

    for (Map.Entry<String, ISlotType> entry : map.entrySet()) {
      ISlotType slotType = entry.getValue();

      if (filter.test(slotType)) {
        result.put(entry.getKey(), slotType);
      }
    }
    return result;
  }

  public static Optional<ICurio> getCurio(ItemStack stack) {
    Item item = stack.getItem();

    if (REGISTRY.containsKey(item)) {
      ItemizedCurioCapability curioCapability =
          new ItemizedCurioCapability(REGISTRY.get(item), stack);
      return Optional.of(curioCapability);
    }
    return Optional.empty();
  }

  public static Optional<ICuriosItemHandler> getCuriosInventory(LivingEntity livingEntity) {

    if (livingEntity != null) {
      return livingEntity.getCapability(CuriosCapability.INVENTORY).resolve();
    } else {
      return Optional.empty();
    }
  }

  public static boolean isStackValid(SlotContext slotContext, ItemStack stack) {
    String id = slotContext.identifier();
    LivingEntity entity = slotContext.entity();
    Map<String, ISlotType> map;

    if (entity != null) {
      map = getItemStackSlots(stack, entity);
    } else {
      map = getItemStackSlots(stack, FMLLoader.getDist() == Dist.CLIENT);
    }
    Set<String> slots = map.keySet();

    if (!slots.isEmpty()) {
      return id.equals("curio") || slots.contains(id) || slots.contains("curio");
    } else if (id.equals("curio")) {
      // If there are no slots available to confirm validity for the generic curio slot,
      // perform fallback checks

      // tags
      if (stack.getTags()
          .anyMatch(tagKey -> tagKey.location().getNamespace().equals(CuriosApi.MODID))) {
        return true;
      }

      // predicates
      Map<String, ISlotType> allSlots = CuriosApi.getSlots(false);
      SlotResult slotResult = new SlotResult(slotContext, stack);

      for (Map.Entry<String, ISlotType> entry : allSlots.entrySet()) {
        ISlotType slotType = entry.getValue();

        for (ResourceLocation validator : slotType.getValidators()) {

          if (CuriosApi.getCurioPredicate(validator).map(val -> val.test(slotResult))
              .orElse(false)) {
            return true;
          }
        }
      }

      // capability
      return CuriosApi.getCurio(stack).isPresent();
    }
    return false;
  }

  public static Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
      SlotContext slotContext, UUID uuid, ItemStack stack) {
    Multimap<Holder<Attribute>, AttributeModifier> multimap = LinkedHashMultimap.create();
    CurioAttributeModifiers attributemodifiers =
        stack.getOrDefault(CuriosRegistry.CURIO_ATTRIBUTE_MODIFIERS.get(),
            CurioAttributeModifiers.EMPTY);

    if (!attributemodifiers.modifiers().isEmpty()) {

      for (CurioAttributeModifiers.Entry modifier : attributemodifiers.modifiers()) {

        if (modifier.slot().equals(slotContext.identifier())) {
          ResourceLocation rl = modifier.attribute();
          AttributeModifier attributeModifier = modifier.modifier();

          if (rl != null) {

            if (uuid.getLeastSignificantBits() != 0L && uuid.getMostSignificantBits() != 0L) {
              AttributeModifier.Operation operation = attributeModifier.operation();
              double amount = attributeModifier.amount();
              String name = attributeModifier.name();

              if (rl.getNamespace().equals("curios")) {
                String identifier1 = rl.getPath();
                LivingEntity livingEntity = slotContext.entity();
                boolean clientSide = livingEntity == null || livingEntity.level().isClientSide();

                if (CuriosApi.getSlot(identifier1, clientSide).isPresent()) {
                  CuriosApi.addSlotModifier(multimap, identifier1, uuid, amount, operation);
                }
              } else {
                Holder<Attribute> attribute =
                    BuiltInRegistries.ATTRIBUTE.getHolder(rl).orElse(null);

                if (attribute != null) {
                  multimap.put(attribute, new AttributeModifier(uuid, name, amount, operation));
                }
              }
            }
          }
        }
      }
    } else {
      multimap = getCurio(stack).map(curio -> curio.getAttributeModifiers(slotContext, uuid))
          .orElse(multimap);
    }
    CurioAttributeModifierEvent evt =
        new CurioAttributeModifierEvent(stack, slotContext, uuid, multimap);
    MinecraftForge.EVENT_BUS.post(evt);
    return LinkedHashMultimap.create(evt.getModifiers());
  }

  public static void addSlotModifier(Multimap<Holder<Attribute>, AttributeModifier> map,
                                     String identifier, UUID uuid, double amount,
                                     AttributeModifier.Operation operation) {
    map.put(SlotAttribute.getOrCreate(identifier),
        new AttributeModifier(uuid, identifier, amount, operation));
  }

  public static void addSlotModifier(ItemStack stack, String identifier, String name, UUID uuid,
                                     double amount, AttributeModifier.Operation operation,
                                     String slot) {
    addModifier(stack, SlotAttribute.getOrCreate(identifier), name, uuid, amount, operation, slot);
  }

  public static void addModifier(ItemStack stack, Holder<Attribute> attribute, String name,
                                 UUID uuid, double amount, AttributeModifier.Operation operation,
                                 String slot) {
    ResourceLocation rl;

    if (attribute.value() instanceof SlotAttribute wrapper) {
      rl = new ResourceLocation("curios:" + wrapper.getIdentifier());
    } else {
      rl = BuiltInRegistries.ATTRIBUTE.getKey(attribute.value());
    }

    if (uuid == null) {
      uuid = UUID.randomUUID();
    }
    AttributeModifier attributeModifier = new AttributeModifier(uuid, name, amount, operation);
    CurioAttributeModifiers.Entry entry =
        new CurioAttributeModifiers.Entry(rl, attributeModifier, slot);
    CurioAttributeModifiers curioAttributeModifiers =
        stack.getOrDefault(CuriosRegistry.CURIO_ATTRIBUTE_MODIFIERS.get(),
            CurioAttributeModifiers.EMPTY);
    List<CurioAttributeModifiers.Entry> list = new ArrayList<>(curioAttributeModifiers.modifiers());
    list.add(entry);
    stack.set(CuriosRegistry.CURIO_ATTRIBUTE_MODIFIERS.get(),
        new CurioAttributeModifiers(list, curioAttributeModifiers.showInTooltip()));
  }

  public static void broadcastCurioBreakEvent(SlotContext slotContext) {
    LivingEntity livingEntity = slotContext.entity();

    if (livingEntity != null) {
      NetworkHandler.INSTANCE.send(
          new SPacketBreak(slotContext.entity().getId(), slotContext.identifier(),
              slotContext.index()),
          PacketDistributor.TRACKING_ENTITY_AND_SELF.with(slotContext.entity()));
    }
  }

  private static final Map<String, UUID> UUIDS = new HashMap<>();

  public static UUID getSlotUuid(SlotContext slotContext) {
    String key = slotContext.identifier() + slotContext.index();
    return UUIDS.computeIfAbsent(key, (k) -> UUID.nameUUIDFromBytes(k.getBytes()));
  }


  private static final Map<ResourceLocation, Predicate<SlotResult>> SLOT_RESULT_PREDICATES =
      new HashMap<>();

  public static void registerCurioPredicate(ResourceLocation resourceLocation,
                                            Predicate<SlotResult> validator) {
    SLOT_RESULT_PREDICATES.putIfAbsent(resourceLocation, validator);
  }

  public static Optional<Predicate<SlotResult>> getCurioPredicate(
      ResourceLocation resourceLocation) {
    return Optional.ofNullable(SLOT_RESULT_PREDICATES.get(resourceLocation));
  }

  public static Map<ResourceLocation, Predicate<SlotResult>> getCurioPredicates() {
    return ImmutableMap.copyOf(SLOT_RESULT_PREDICATES);
  }

  public static boolean testCurioPredicates(Set<ResourceLocation> predicates,
                                            SlotResult slotResult) {

    for (ResourceLocation id : predicates) {

      if (CuriosApi.getCurioPredicate(id).map(
          slotResultPredicate -> slotResultPredicate.test(slotResult)).orElse(false)) {
        return true;
      }
    }
    return false;
  }

  static {
    registerCurioPredicate(new ResourceLocation(CuriosApi.MODID, "all"), (slotResult) -> true);
    registerCurioPredicate(new ResourceLocation(CuriosApi.MODID, "none"),
        (slotResult) -> false);
    registerCurioPredicate(new ResourceLocation(CuriosApi.MODID, "tag"), (slotResult) -> {
      String id = slotResult.slotContext().identifier();
      TagKey<Item> tag1 = ItemTags.create(new ResourceLocation(CuriosApi.MODID, id));
      TagKey<Item> tag2 = ItemTags.create(new ResourceLocation(CuriosApi.MODID, "curio"));
      ItemStack stack = slotResult.stack();
      return stack.is(tag1) || stack.is(tag2);
    });
  }
}
