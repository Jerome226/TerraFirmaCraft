package net.dries007.tfc.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.fluids.FluidStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.dries007.tfc.client.RenderHelpers;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.capabilities.heat.Heat;
import net.dries007.tfc.common.container.CrucibleContainer;
import net.dries007.tfc.util.AlloyView;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.Metal;

public class CrucibleScreen extends BlockEntityScreen<CrucibleBlockEntity, CrucibleContainer>
{
    private static final ResourceLocation BACKGROUND = Helpers.identifier("textures/gui/crucible.png");
    private static final int MAX_ELEMENTS = 3;

    private int scrollPos;
    private boolean scrollPress;

    public CrucibleScreen(CrucibleContainer container, Inventory playerInventory, Component name)
    {
        super(container, playerInventory, name, BACKGROUND);

        inventoryLabelY += 55;
        imageHeight += 55;

        scrollPos = 0;
        scrollPress = false;
    }

    @Override
    protected void renderLabels(PoseStack stack, int mouseX, int mouseY)
    {
        // No-op - this screen basically doesn't have room for the inventory labels... how sad
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (mouseX >= leftPos + 154 && mouseX <= leftPos + 165 && mouseY >= topPos + 11 + scrollPos && mouseY <= topPos + 26 + scrollPos)
        {
            scrollPress = true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        if (scrollPress)
        {
            scrollPos = Math.min(Math.max((int) mouseY - topPos - 18, 0), 49);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (scrollPress && button == 0)
        {
            scrollPress = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(PoseStack stack, float partialTicks, int mouseX, int mouseY)
    {
        super.renderBg(stack, partialTicks, mouseX, mouseY);

        // Draw the temperature indicator
        int temperature = (int) (51 * tile.getSyncableData().get(CrucibleBlockEntity.DATA_SLOT_TEMPERATURE) / Heat.maxVisibleTemperature());
        if (temperature > 0)
        {
            blit(stack, leftPos + 7, topPos + 131 - Math.min(temperature, 51), 176, 0, 15, 5);
        }

        // Draw the scroll bar
        blit(stack, leftPos + 154, topPos + 11 + scrollPos, 176, 7, 12, 15);

        // Draw the fluid + detailed content
        AlloyView alloy = tile.getAlloy();
        if (alloy.getAmount() > 0)
        {
            int startX = 97;
            int startY = 93;
            int endX = 133;
            int endY = 124;

            int fillHeight = (int) Math.ceil((float) (endY - startY) * alloy.getAmount() / alloy.getMaxUnits());

            final FluidStack fluid = alloy.getResultAsFluidStack();
            final TextureAtlasSprite sprite = getMinecraft().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(fluid.getFluid().getAttributes().getStillTexture(fluid));

            RenderHelpers.setShaderColor(fluid.getFluid().getAttributes().getColor(fluid));
            RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);

            int yPos = endY;
            while (fillHeight > 0)
            {
                int yPixels = Math.min(fillHeight, 16);
                int fillWidth = endX - startX;
                int xPos = endX;
                while (fillWidth > 0)
                {
                    int xPixels = Math.min(fillWidth, 16);
                    blit(stack, leftPos + xPos - xPixels, topPos + yPos - yPixels, 0, xPixels, yPixels, sprite);
                    fillWidth -= 16;
                    xPos -= 16;
                }
                fillHeight -= 16;
                yPos -= 16;
            }

            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderTexture(0, texture);

            // Draw Title:
            final Metal result = alloy.getResult();
            final String resultText = ChatFormatting.UNDERLINE + I18n.get(result.getTranslationKey());
            font.draw(stack, resultText, leftPos + 10, topPos + 11, 0x000000);

            int startElement = Math.max(0, (int) Math.floor(((alloy.getMetals().size() - MAX_ELEMENTS) / 49D) * (scrollPos + 1)));

            // Draw Components
            yPos = topPos + 22;
            int index = -1; // So the first +1 = 0
            for (Object2DoubleMap.Entry<Metal> entry : alloy.getMetals().object2DoubleEntrySet())
            {
                index++;
                if (index < startElement)
                {
                    continue;
                }
                if (index > startElement - 1 + MAX_ELEMENTS)
                {
                    break;
                }

                // Draw the content, format:
                // Metal name:
                //   XXX units(YY.Y)%
                // Metal 2 name:
                //   ZZZ units(WW.W)%

                String metalName = font.plainSubstrByWidth(I18n.get(entry.getKey().getTranslationKey()), 141);
                metalName += ":";
                String units;
                if (entry.getDoubleValue() >= 1)
                {
                    units = I18n.get("tfc.tooltip.fluid_units", (int) entry.getDoubleValue());
                }
                else
                {
                    units = I18n.get("tfc.tooltip.less_than_one_fluid_units");
                }
                String content = String.format("  %s(%s%2.1f%%%s)", units, ChatFormatting.DARK_GREEN, 100 * entry.getDoubleValue() / alloy.getAmount(), ChatFormatting.RESET);
                font.draw(stack, metalName, leftPos + 10, yPos, 0x404040);
                font.draw(stack, content, leftPos + 10, yPos + 9, 0x404040);
                yPos += 18;
            }
        }
    }
}