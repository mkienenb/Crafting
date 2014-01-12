package org.terasology.craft.events.crafting;

import org.terasology.entitySystem.event.AbstractConsumableEvent;
import org.terasology.entitySystem.entity.EntityRef;

/**
 * @author Small-Jeeper
 */
public class CheckRefinementEvent extends AbstractConsumableEvent {
    private EntityRef target;
    private EntityRef instigator;


    public CheckRefinementEvent(EntityRef target, EntityRef instigator) {
        this.target = target;
        this.instigator = instigator;
    }


    public EntityRef getTarget() {
        return target;
    }

    public EntityRef getInstigator() {
        return instigator;
    }

}