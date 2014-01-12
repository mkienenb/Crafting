package org.terasology.craft.componentSystem.controllers;

import org.newdawn.slick.Color;
import org.terasology.asset.Assets;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.EventPriority;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.ComponentSystem;
import org.terasology.entitySystem.systems.In;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.RenderSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.players.LocalPlayerSystem;
import org.terasology.logic.players.PlayerInventorySystem;
import org.terasology.logic.health.HealthComponent;
import org.terasology.logic.inventory.InventoryComponent;
import org.terasology.logic.inventory.ItemComponent;
import org.terasology.logic.inventory.SlotBasedInventoryManager;
import org.terasology.craft.components.actions.CraftingActionComponent;
import org.terasology.craft.events.crafting.AddItemEvent;
import org.terasology.craft.events.crafting.ChangeLevelEvent;
import org.terasology.craft.events.crafting.CheckRefinementEvent;
import org.terasology.craft.events.crafting.DeleteItemEvent;
import org.terasology.craft.rendering.CraftingGrid;
import org.terasology.world.block.items.BlockItemFactory;
import org.terasology.logic.common.ActivateEvent;
import org.terasology.engine.CoreRegistry;
import org.terasology.engine.Time;
import org.terasology.input.ButtonState;
import org.terasology.input.cameraTarget.CameraTargetSystem;
import org.terasology.input.binds.*;
import org.terasology.input.binds.interaction.AttackButton;
import org.terasology.input.binds.inventory.DropItemButton;
import org.terasology.input.binds.inventory.ToolbarNextButton;
import org.terasology.input.binds.inventory.ToolbarPrevButton;
import org.terasology.input.binds.inventory.UseItemButton;
import org.terasology.input.binds.movement.RunButton;
import org.terasology.input.events.MouseXAxisEvent;
import org.terasology.input.events.MouseYAxisEvent;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.logic.manager.GUIManager;
import org.terasology.math.AABB;
import org.terasology.network.ClientComponent;
import org.terasology.logic.characters.CharacterComponent;
import org.terasology.logic.characters.CharacterMovementComponent;
import org.terasology.rendering.gui.framework.UIDisplayContainer;
import org.terasology.rendering.gui.framework.UIDisplayElement;
import org.terasology.rendering.gui.layout.ChooseRowLayout;
import org.terasology.rendering.gui.widgets.UICompositeScrollable;
import org.terasology.rendering.gui.widgets.UIImage;
import org.terasology.rendering.gui.widgets.UIInventoryGrid;
import org.terasology.rendering.gui.widgets.UIWindow;
import org.terasology.rendering.AABBRenderer;
import org.terasology.rendering.BlockOverlayRenderer;
import org.terasology.rendering.world.WorldRenderer;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.items.BlockItemComponent;
import org.terasology.world.block.BlockManager;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

// TODO: for this system, I changed LocalPlayerComponent to ClientComponent.local as it seems to be specific to the input provider

/**
 * @author Adeon
 */
@RegisterSystem(RegisterMode.AUTHORITY)
public class CraftingSystem implements UpdateSubscriberSystem, RenderSystem, ComponentSystem {

    @In
    private BlockManager blockManager;

    @In
    private LocalPlayer localPlayer;
    
    @In
    private PlayerInventorySystem playerInventorySystem;
    
    @In
    private SlotBasedInventoryManager slotBasedInventoryManager;

    @In
    private CameraTargetSystem cameraTargetSystem;

    @In
    private Time timer;

    @In
    private WorldProvider worldProvider;

    @In
    private WorldRenderer worldRenderer;

    @In
    private LocalPlayerSystem localPlayerSystem;

    @In
    private GUIManager guiManager;

    private boolean useButtonPushed = false;
    private boolean dropButtonPushed = false;
    private boolean toolbarChanged = false;
    private boolean itsCraftActionTime = false;
    private int craftIncreaseDecreaseSpeed = 1;
    private boolean dontDrop = false;
    private long lastTimeShowInventory, lastTimeThrowInteraction, lastInteraction;
    private boolean additionalOptions = false;

    private static enum statesCreateBlock {
        NOT_READY,
        READY,
        CREATED,
    }

    ;

    private statesCreateBlock stateCreateBlock = statesCreateBlock.NOT_READY;

    //Todo: Remove this

    private boolean isGUIInit = false;

    /*Craft UI*/
    private UIInventoryGrid inventory;
    private UIImage craftingCloudBackground;
    private UIImage craftingResultBackground;
    private UIImage craftingArrow;
    private UICompositeScrollable miniInventory;
    private UIInventoryGrid craftElement;

    private BlockOverlayRenderer craftingGridRenderer = new CraftingGrid();
    private BlockOverlayRenderer aabbRenderer = new AABBRenderer(AABB.createEmpty());

    @Override
    public void initialise() {
    }

    @ReceiveEvent(components = ClientComponent.class, priority = EventPriority.PRIORITY_HIGH)
    public void onMouseX(MouseXAxisEvent event, EntityRef entity) {
        if (entity.getComponent(ClientComponent.class).local) {
            if (event.getTarget().hasComponent(CraftingActionComponent.class)) {
                event.getTarget().send(new CheckRefinementEvent(event.getTarget(), entity));
            }
        }
    }


    @ReceiveEvent(components = ClientComponent.class, priority = EventPriority.PRIORITY_HIGH)
    public void onMouseY(MouseYAxisEvent event, EntityRef entity) {
        if (entity.getComponent(ClientComponent.class).local) {
            if (event.getTarget().hasComponent(CraftingActionComponent.class)) {
                event.getTarget().send(new CheckRefinementEvent(event.getTarget(), entity));
            }
    
            if (itsCraftActionTime) {
                craftIncreaseDecreaseSpeed += event.getValue();
                if (craftIncreaseDecreaseSpeed > 32) {
                    craftIncreaseDecreaseSpeed = 32;
                }
    
                if (craftIncreaseDecreaseSpeed < 1) {
                    craftIncreaseDecreaseSpeed = 1;
                }
    
                event.consume();
            }
        }
    }

    @ReceiveEvent(components = { ClientComponent.class, CharacterMovementComponent.class}, priority = EventPriority.PRIORITY_HIGH)
    public void onRun(RunButton event, EntityRef entity) {
        if (entity.getComponent(ClientComponent.class).local) {
            additionalOptions = event.isDown();
        }
    }

    @ReceiveEvent(components = {ClientComponent.class, InventoryComponent.class}, priority = EventPriority.PRIORITY_HIGH)
    public void onAttackRequest(AttackButton event, EntityRef entity) {
        if (entity.getComponent(ClientComponent.class).local) {
            CraftingActionComponent craftingComponent = event.getTarget().getComponent(CraftingActionComponent.class);
            boolean itsCraftBlock = craftingComponent != null;
            if (!event.isDown() || timer.getGameTimeInMs() - lastInteraction < 200 / craftIncreaseDecreaseSpeed) {
                if (!event.isDown()) {
                    craftIncreaseDecreaseSpeed = 1;
                    itsCraftActionTime = false;
                    if (itsCraftBlock) {
                        if (additionalOptions) {
                            event.getTarget().send(new ChangeLevelEvent(-1, entity));
                        }
                        event.consume();
                        return;
                    }
                }

                if (itsCraftBlock) {
                    event.consume();
                }
                return;
            }

            if (itsCraftBlock) {
                if (!additionalOptions && timer.getGameTimeInMs() - lastInteraction > (200 / craftIncreaseDecreaseSpeed)) {
                    // if (localPlayerComp.isDead) return;
                    HealthComponent healthComponent = entity.getComponent(HealthComponent.class);
                    if ((healthComponent != null) && (healthComponent.currentHealth <= 0)) {
                        return;
                    }

                    float dropPower = getDropPower();
                    event.getTarget().send(new DeleteItemEvent(dropPower / 6));
                    dontDrop = true;
                    resetDropMark();
                    lastInteraction = timer.getGameTimeInMs();
                    itsCraftActionTime = true;
                }
                event.consume();
            }
        }
    }

    @ReceiveEvent(components = {ClientComponent.class}, priority = EventPriority.PRIORITY_HIGH)
    public void onDropItem(DropItemButton event, EntityRef entity) {

        if (entity.getComponent(ClientComponent.class).local) {
            ClientComponent localPlayerComp = entity.getComponent(ClientComponent.class);
    
            // if (localPlayerComp.isDead) return;
            HealthComponent healthComponent = entity.getComponent(HealthComponent.class);
            if ((healthComponent != null) && (healthComponent.currentHealth <= 0)) {
                return;
            }

            CraftingActionComponent craftingComponent = event.getTarget().getComponent(CraftingActionComponent.class);
            if (null == craftingComponent) {
                event.getTarget().addComponent(new CraftingActionComponent());
                craftingComponent = event.getTarget().getComponent(CraftingActionComponent.class);
            }

            if (event.isDown()) {
                if (stateCreateBlock == statesCreateBlock.CREATED) {
                    resetDropMark();
                    event.consume();
                    return;
                }
    
                dropButtonPushed = true;
    
                if (craftingComponent == null) {
                    stateCreateBlock = statesCreateBlock.READY;
                    return;
                }
    
                if (craftingComponent != null) {
    
                    if (lastTimeThrowInteraction == 0) {
                        lastTimeThrowInteraction = timer.getGameTimeInMs();
                    }
    
                    UIImage crossHair = (UIImage) CoreRegistry.get(GUIManager.class).getWindowById("hud").getElementById("crosshair");
    
                    crossHair.getTextureSize().set(new Vector2f(22f / 256f, 22f / 256f));
                    float dropPower = getDropPower();
                    crossHair.getTextureOrigin().set(new Vector2f((46f + 22f * dropPower) / 256f, 23f / 256f));
                    //event.consume();
                    return;
                }
            } else {
                dropButtonPushed = false;
    
                if (!useButtonPushed && stateCreateBlock == statesCreateBlock.CREATED) {
                    stateCreateBlock = statesCreateBlock.NOT_READY;
                    playerInventorySystem.resetDropMark();
                    resetDropMark();
                    event.consume();
                    return;
                }
    
                if (craftingComponent == null) {
                    return;
                }
    
                if (craftingComponent != null) {
                    float dropPower = getDropPower();
                    if (stateCreateBlock != statesCreateBlock.CREATED) {
                        playerInventorySystem.resetDropMark();
                        resetDropMark();
                        event.consume();
                        return;
                    }
                    if (dontDrop) {
                        playerInventorySystem.resetDropMark();
                        resetDropMark();
                        dontDrop = false;
                        return;
                    }
    
                    dropPower *= 25f;
                    resetDropMark();
                    playerInventorySystem.resetDropMark();
                    event.consume();
                }
            }
        }
    }


    @ReceiveEvent(components = {ClientComponent.class}, priority = EventPriority.PRIORITY_HIGH)
    public void onNextItem(ToolbarNextButton event, EntityRef entity) {
        if (entity.getComponent(ClientComponent.class).local) {
            if (additionalOptions && cameraTargetSystem.getTarget().getComponent(CraftingActionComponent.class) != null) {
                UIInventoryGrid toolbar = (UIInventoryGrid) guiManager.getWindowById("hud").getElementById("toolbar");

                int slotStart = toolbar.getStartSlot();
                int slotEnd = toolbar.getCells().size() - 1; // TODO: it's unclear if this is equivalent to getSlotEnd();

                slotStart += 10;
                slotEnd += 10;

                int numSlots = slotBasedInventoryManager.getNumSlots(localPlayer.getCharacterEntity());
                if (slotEnd > numSlots - 1) {
                    slotStart = 0;
                    slotEnd = 9;
                }

                toolbar.linkToEntity(localPlayer.getCharacterEntity(), slotStart, slotEnd);
                toolbarChanged = true;
                showSmallInventory((slotEnd + 1) / 10);
                event.consume();
            }

            if (cameraTargetSystem.getTarget().hasComponent(CraftingActionComponent.class)) {
                cameraTargetSystem.getTarget().send(new CheckRefinementEvent(cameraTargetSystem.getTarget(), entity));
            }
        }
    }

    @ReceiveEvent(components = {ClientComponent.class}, priority = EventPriority.PRIORITY_HIGH)
    public void onPrevItem(ToolbarPrevButton event, EntityRef entity) {
        if (entity.getComponent(ClientComponent.class).local) {
            if (additionalOptions && cameraTargetSystem.getTarget().getComponent(CraftingActionComponent.class) != null) {
                InventoryComponent inventory = localPlayer.getCharacterEntity().getComponent(InventoryComponent.class);
                UIInventoryGrid toolbar = (UIInventoryGrid) guiManager.getWindowById("hud").getElementById("toolbar");
    
                int slotStart = toolbar.getStartSlot();
                int slotEnd = toolbar.getCells().size() - 1; // TODO: it's unclear if this is equivalent to getSlotEnd();

                slotStart -= 10;
                slotEnd -= 10;
    
                if (slotStart < -1) {
                    int numSlots = slotBasedInventoryManager.getNumSlots(localPlayer.getCharacterEntity());
                    slotStart = numSlots - 10;
                    slotEnd = numSlots - 1;
                }
    
                if (slotStart < 0) {
                    slotStart = 0;
                }
    
                toolbar.linkToEntity(localPlayer.getCharacterEntity(), slotStart, slotEnd);
                toolbarChanged = true;
                showSmallInventory((slotEnd + 1) / 10);
                event.consume();
            }
    
            if (cameraTargetSystem.getTarget().hasComponent(CraftingActionComponent.class)) {
                cameraTargetSystem.getTarget().send(new CheckRefinementEvent(cameraTargetSystem.getTarget(), entity));
            }
        }
    }

    @ReceiveEvent(components = {ClientComponent.class, InventoryComponent.class}, priority = EventPriority.PRIORITY_CRITICAL)
    public void onUseItemRequest(UseItemButton event, EntityRef entity) {
        if (entity.getComponent(ClientComponent.class).local) {

            CraftingActionComponent craftingComponent = event.getTarget().getComponent(CraftingActionComponent.class);
            if (!event.isDown() || timer.getGameTimeInMs() - lastInteraction < 200 / craftIncreaseDecreaseSpeed) {
                if (!event.isDown()) {
                    useButtonPushed = false;
                    itsCraftActionTime = false;
                    craftIncreaseDecreaseSpeed = 1;
    
                    if (!useButtonPushed && stateCreateBlock == statesCreateBlock.CREATED) {
                        stateCreateBlock = statesCreateBlock.NOT_READY;
                        resetDropMark();
                        event.consume();
                        return;
                    }
    
                    if (craftingComponent != null && additionalOptions) {
                        event.getTarget().send(new ChangeLevelEvent(1, entity));
                        event.consume();
                        return;
                    }
                }
                if (craftingComponent != null) {
                    event.consume();
                }
                return;
            }
    
            if (stateCreateBlock == statesCreateBlock.CREATED) {
                event.consume();
                return;
            }
    
            if (craftingComponent != null) {
                useButtonPushed = true;
                if (timer.getGameTimeInMs() - lastInteraction > (200 / craftIncreaseDecreaseSpeed) && !additionalOptions) {
                    CharacterComponent crafterCharacter = entity.getComponent(CharacterComponent.class);
                    // if (localPlayerComp.isDead) return;
                    HealthComponent healthComponent = entity.getComponent(HealthComponent.class);
                    if ((healthComponent != null) && (healthComponent.currentHealth <= 0)) {
                        return;
                    }
                    InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                    UIInventoryGrid toolbar = (UIInventoryGrid) guiManager.getWindowById("hud").getElementById("toolbar");
    
                    EntityRef selectedItemEntity = slotBasedInventoryManager.getItemInSlot(entity, crafterCharacter.selectedItem + toolbar.getStartSlot());
    
                    if (!selectedItemEntity.equals(EntityRef.NULL)) {
                        float dropPower = getDropPower();
                        itsCraftActionTime = true;
                        event.getTarget().send(new AddItemEvent(event.getTarget(), selectedItemEntity, dropPower / 6));
                        resetDropMark();
                        lastInteraction = timer.getGameTimeInMs();
                    }
                }
                event.consume();
            } else {
    
                if (stateCreateBlock == statesCreateBlock.READY) {
                    createCraftBlock(event, entity);
                }
    
            }
        }
    }

    private void createCraftBlock(UseItemButton event, EntityRef playerEntity) {
        if (event.getState() != ButtonState.DOWN) {
            return;
        }

        CharacterComponent crafterCharacter = playerEntity.getComponent(CharacterComponent.class);
        // if (localPlayerComp.isDead) return;
        HealthComponent healthComponent = playerEntity.getComponent(HealthComponent.class);
        if ((healthComponent != null) && (healthComponent.currentHealth <= 0)) {
            return;
        }

        EntityRef target = event.getTarget();

        BlockComponent block = target.getComponent(BlockComponent.class);

        if (block != null) {
            Block currentBlock = worldProvider.getBlock(block.getPosition());

            EntityManager entityManager = CoreRegistry.get(EntityManager.class);

            if (!currentBlock.isInvisible() &&
                    !currentBlock.isLiquid() &&
                    !currentBlock.isPenetrable()
// Can a player craft on this block -- just let it always be true until we show there's a need for something else                    
//                     && currentBlock.isCraftPlace()
                    ) {
                InventoryComponent inventory = playerEntity.getComponent(InventoryComponent.class);
                // if (localPlayerComp.isDead) return;
                HealthComponent playerHealthComponent = playerEntity.getComponent(HealthComponent.class);
                if ((playerHealthComponent != null) && (playerHealthComponent.currentHealth <= 0)) {
                    return;
                }

                EntityRef selectedItemEntity = slotBasedInventoryManager.getItemInSlot(playerEntity, crafterCharacter.selectedItem);

                ItemComponent item = selectedItemEntity.getComponent(ItemComponent.class);

                if (item == null) {
                    return;
                }

                BlockItemFactory blockFactory = new BlockItemFactory(entityManager);
                EntityRef craftEntity = blockFactory.newInstance(blockManager.getBlockFamily("craft:craft"));

                BlockItemComponent blockItemComponent = craftEntity.getComponent(BlockItemComponent.class);

                // TODO: IMPORTANT: not sure what to do with placedEntity
//                if (!blockItemComponent.placedEntity.hasComponent(CraftingActionComponent.class)) {
//                    blockItemComponent.placedEntity.addComponent(new CraftingActionComponent());
//                }

                // TODO: IMPORTANT: not sure what to do with placedEntity
                // EntityRef placedEntity = blockItemComponent.placedEntity;

                if (craftEntity.exists()) {
                    craftEntity.send(new ActivateEvent(target, selectedItemEntity, new Vector3f(worldRenderer.getActiveCamera().getPosition()), new Vector3f(worldRenderer.getActiveCamera().getPosition()), event.getHitPosition(), event.getHitNormal()));
                    // TODO: IMPORTANT: not sure what to do with placedEntity
                    // placedEntity.send(new ActivateEvent(target, selectedItemEntity, new Vector3f(worldRenderer.getActiveCamera().getPosition()), new Vector3f(worldRenderer.getActiveCamera().getPosition()), event.getHitPosition(), event.getHitNormal()));
                    craftEntity.destroy();

                    if (item.stackCount <= 0) {
                        selectedItemEntity.destroy();
                    }

                    stateCreateBlock = statesCreateBlock.CREATED;
                }
                lastInteraction = timer.getGameTimeInMs();
                crafterCharacter.handAnimation = 0.5f;
                playerEntity.saveComponent(crafterCharacter);
                event.consume();
            }
        }
    }

    private boolean updateLoopFirstTime = true;
    
    @Override
    public void update(float delta) {

        if (updateLoopFirstTime) {
            updateLoopFirstTime = true;
            return;
        }
        
        // Need to delay gui init until there's some gui to work with
        if (!isGUIInit) {
            initCraftUI();
            isGUIInit = true;
        }

        if (!localPlayer.isValid())
            return;

        EntityRef entity = localPlayer.getCharacterEntity();
        // if (localPlayerComponent.isDead) return;
        HealthComponent healthComponent = entity.getComponent(HealthComponent.class);
        if ((healthComponent != null) && (healthComponent.currentHealth <= 0)) {
            return;
        }

        boolean craftingBlockIsTarget = cameraTargetSystem.getTarget().getComponent(CraftingActionComponent.class) != null;

        if (toolbarChanged && !craftingBlockIsTarget) {
            UIInventoryGrid toolbar = (UIInventoryGrid) guiManager.getWindowById("hud").getElementById("toolbar");
            toolbar.linkToEntity(localPlayer.getCharacterEntity(), 0, 9);
            toolbarChanged = false;
        }

        UIDisplayContainer inventory = (UIDisplayContainer) guiManager.getWindowById("hud").getElementById("hud:inventory");

        if (inventory.isVisible() && timer.getGameTimeInMs() - lastTimeShowInventory >= 1500) {
            inventory.setVisible(false);
        }

        if (craftingBlockIsTarget) {
            if (!localPlayerSystem.getAABBRenderer().equals(craftingGridRenderer)) {
                localPlayerSystem.setAABBRenderer(craftingGridRenderer);
            }
        } else {
            if (!localPlayerSystem.getAABBRenderer().equals(aabbRenderer)) {
                localPlayerSystem.setAABBRenderer(aabbRenderer);
            }
        }

    }

    private void showSmallInventory(int position) {
        UICompositeScrollable inventory = (UICompositeScrollable) guiManager.getWindowById("hud").getElementById("hud:inventory");
        inventory.setVisible(true);
        ((ChooseRowLayout) inventory.getLayout()).getPosition().y = (position - 1) * 36f;
        lastTimeShowInventory = timer.getGameTimeInMs();
    }

    private void resetDropMark() {
        UIImage crossHair = (UIImage) guiManager.getWindowById("hud").getElementById("crosshair");
        lastTimeThrowInteraction = 0;
        crossHair.getTextureSize().set(new Vector2f(20f / 256f, 20f / 256f));
        crossHair.getTextureOrigin().set(new Vector2f(24f / 256f, 24f / 256f));
        playerInventorySystem.resetDropMark();
    }

    private float getDropPower() {
        if (lastTimeThrowInteraction == 0) {
            return 0;
        }
        float dropPower = (float) Math.floor((timer.getGameTimeInMs() - lastTimeThrowInteraction) / 200);

        if (dropPower > 6) {
            dropPower = 6;
        }

        return dropPower;
    }

    private void initCraftUI() {
        miniInventory = new UICompositeScrollable();
        miniInventory.setId("hud:inventory");
        miniInventory.setSize(new Vector2f(360f, 152f));
        miniInventory.setLayout(new ChooseRowLayout(new Vector2f(0f, -152f), new Vector2f(360f, 36f), new Color(255f, 0f, 0f), 4f));
        miniInventory.setHorizontalAlign(UIDisplayElement.EHorizontalAlign.CENTER);
        miniInventory.setVerticalAlign(UIDisplayElement.EVerticalAlign.BOTTOM);
        miniInventory.setPosition(new Vector2f(0f, -44f));
        miniInventory.setVisible(false);

        inventory = new UIInventoryGrid(10);
        // TODO: Setting the icon position isn't currently supported.  Not sure if it makes sense anyway since the icons are a constant size
//        inventory.setIconPosition(new Vector2f(-4f, -4f));
        inventory.setVisible(true);
        inventory.setCellMargin(new Vector2f(0, 0));
        inventory.setBorderImage("engine:inventory", new Vector2f(0f, 84f), new Vector2f(169f, 61f), new Vector4f(5f, 4f, 3f, 4f));

        // TODO: Setting the cell size isn't currently supported.  Not sure if it makes sense anyway since the icons are a constant size
//        inventory.setCellSize(new Vector2f(36f, 36f));
        inventory.linkToEntity(CoreRegistry.get(LocalPlayer.class).getCharacterEntity(), 0);
        miniInventory.addDisplayElement(inventory);

        craftElement = new UIInventoryGrid(1);
        craftElement.setId("craftElement");
        craftElement.setHorizontalAlign(UIDisplayElement.EHorizontalAlign.CENTER);
        craftElement.setVerticalAlign(UIDisplayElement.EVerticalAlign.TOP);
        craftElement.setCellMargin(new Vector2f(0f, 0f));
        craftElement.setPosition(new Vector2f(55f, 60f));
        // TODO: Setting the icon position isn't currently supported.  Not sure if it makes sense anyway since the icons are a constant size
//        craftElement.setIconPosition(new Vector2f(-4f, -4f));
        // TODO: Setting the cell size isn't currently supported.  Not sure if it makes sense anyway since the icons are a constant size
//        craftElement.setCellSize(new Vector2f(36f, 36f));
        craftElement.setVisible(false);

        craftingArrow = new UIImage(Assets.getTexture("craft:gui_craft"));
        craftingArrow.setSize(new Vector2f(70f, 33f));
        craftingArrow.setTextureOrigin(new Vector2f(186f, 0f));
        craftingArrow.setTextureSize(new Vector2f(70f, 33f));
        craftingArrow.setId("craftingArrow");
        craftingArrow.setHorizontalAlign(UIDisplayElement.EHorizontalAlign.CENTER);
        craftingArrow.setVerticalAlign(UIDisplayElement.EVerticalAlign.TOP);
        craftingArrow.setPosition(new Vector2f(-5f, 60f));
        craftingArrow.setVisible(false);

        craftingResultBackground = new UIImage(Assets.getTexture("craft:gui_craft"));
        craftingResultBackground.setSize(new Vector2f(40f, 40f));
        craftingResultBackground.setTextureOrigin(new Vector2f(111f, 0f));
        craftingResultBackground.setTextureSize(new Vector2f(75f, 75f));
        craftingResultBackground.setId("craftingResultBackground");
        craftingResultBackground.setHorizontalAlign(UIDisplayElement.EHorizontalAlign.CENTER);
        craftingResultBackground.setVerticalAlign(UIDisplayElement.EVerticalAlign.TOP);
        craftingResultBackground.setPosition(new Vector2f(-70f, 60f));
        craftingResultBackground.setVisible(false);

        craftingCloudBackground = new UIImage(Assets.getTexture("craft:gui_craft"));
        craftingCloudBackground.setSize(new Vector2f(222f, 134f));
        craftingCloudBackground.setTextureOrigin(new Vector2f(0f, 92f));
        craftingCloudBackground.setTextureSize(new Vector2f(111f, 67f));
        craftingCloudBackground.setId("craftingCloudBackground");
        craftingCloudBackground.setHorizontalAlign(UIDisplayElement.EHorizontalAlign.CENTER);
        craftingCloudBackground.setVerticalAlign(UIDisplayElement.EVerticalAlign.TOP);
        craftingCloudBackground.setPosition(new Vector2f(0f, 0f));
        craftingCloudBackground.setVisible(false);

        if (null == guiManager) {
            throw new RuntimeException("guiManager is null");
        }
        UIWindow hudWindow = guiManager.getWindowById("hud");
        if (null == hudWindow) {
            throw new RuntimeException("hudWindow is null");
        }
        hudWindow.addDisplayElement(craftingCloudBackground);
        hudWindow.addDisplayElement(craftingResultBackground);
        hudWindow.addDisplayElement(craftingArrow);
        hudWindow.addDisplayElement(miniInventory);
        hudWindow.addDisplayElement(craftElement);
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void renderOpaque() {
    }

    @Override
    public void renderAlphaBlend() {
    }

    @Override
    public void renderOverlay() {
    }

    @Override
    public void renderFirstPerson() {
    }

    @Override
    public void renderShadows() {
    }

}
