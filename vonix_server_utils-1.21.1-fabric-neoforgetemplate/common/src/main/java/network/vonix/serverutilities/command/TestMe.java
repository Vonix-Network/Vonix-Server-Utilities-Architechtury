package network.vonix.serverutilities.command;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public class TestMe {
    public static void test(ItemContainerContents contents) {
        NonNullList<ItemStack> list = NonNullList.withSize(54, ItemStack.EMPTY);
        contents.copyInto(list);
    }
}
