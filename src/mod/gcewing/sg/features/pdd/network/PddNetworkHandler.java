package gcewing.sg.features.pdd.network;

import gcewing.sg.BaseDataChannel;
import gcewing.sg.SGCraft;
import gcewing.sg.features.pdd.AddressData;
import gcewing.sg.features.pdd.client.gui.PddEntryScreen;
import gcewing.sg.features.pdd.client.gui.PddScreen;
import gcewing.sg.network.SGChannel;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.SGAddressing;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public class PddNetworkHandler extends SGChannel {

    protected static BaseDataChannel pddChannel;

    public PddNetworkHandler(String name) {
        super(name);
        pddChannel = this;
    }

    public static void addPddEntryFromServer(EntityPlayer player, String address) {
        ChannelOutput data = pddChannel.openPlayer(player,"AddPddEntry");
        data.writeUTF(address);
        data.close();
    }

    @ClientMessageHandler("AddPddEntry")
    public void handlePddAddAddressRequest(EntityPlayer player, ChannelInput data) {
        String address = data.readUTF();
        address = SGAddressing.formatAddress(address, "-", "-");
        new PddEntryScreen(null, player, "Name Here", address, 10, 0, false, false).display();
    }

    public static void updatePdd(EntityPlayer player, boolean value, int status) {
        ChannelOutput data = pddChannel.openPlayer(player,"UpdatePdd");
        data.writeInt(status);
        data.writeBoolean(value);
        data.close();
    }

    @ClientMessageHandler("UpdatePdd")
    public void handleUpdatePddListRequest(EntityPlayer player, ChannelInput data) {
        int status = data.readInt();
        boolean update = data.readBoolean();
        if (Minecraft.getMinecraft().currentScreen instanceof PddScreen) {
            PddScreen screen = (PddScreen) Minecraft.getMinecraft().currentScreen;
            if (status == 1) // Update Address List
                screen.delayedUpdate();
            if (status == 2)
                screen.stopDialing();
        }
    }

    public static void sendPddInputToServer(SGBaseTE te, int function, String origin, String destination) {
        ChannelOutput data = pddChannel.openServer("PddInput");
        writeCoords(data, te);
        data.writeInt(function);
        data.writeUTF(origin);
        data.writeUTF(destination);
        data.close();
    }

    @ServerMessageHandler("PddInput") public void handlePddInputFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        int setting = data.readInt();
        SGBaseTE localGate = SGBaseTE.at(player.world, pos);
        String origin = data.readUTF();
        String destination = data.readUTF();

        if (!origin.isEmpty() && !destination.isEmpty()) {
            if (SGAddressing.inSameDimension(origin, destination)) {
                destination = destination.substring(0, 7);
            }
        }

        if (!SGCraft.hasPermission(player, "sgcraft.gui.pdd")) {
            System.err.println("SGCraft - Hacked Client detected!");
            return;
        }

        if (setting == 1) { // Connect / Dial / Double Click
            if (!localGate.isConnected()) {
                localGate.connect(destination, player);
            }
        }
        if (setting == 2) {
            if (localGate.isConnected()) {
                localGate.disconnect(player);
            }
        }
        if (setting == 3) {
            localGate.connectOrDisconnect("", player);
            localGate.errorState = false; // Force this on the servers' TE.
        }

        if (setting == 4) {
            localGate.clearIdleConnection();
            System.out.println("Cleared Idle Connection");
            localGate.errorState = false; // Force this on the servers' TE.
        }
    }

    public static void sendPddEntryUpdateToServer(String name, String address, int index, int unid, boolean locked) {
        ChannelOutput data = pddChannel.openServer("PddInputEntry");
        data.writeUTF(name);
        data.writeUTF(address);
        data.writeInt(index);
        data.writeInt(unid);
        data.writeBoolean(locked);
        data.close();
    }

    @ServerMessageHandler("PddInputEntry")
    public void handlePddEntryUpdateFromClient(EntityPlayer player, ChannelInput data) {
        String name = data.readUTF();
        String address = data.readUTF();
        int index = data.readInt();
        int unid = data.readInt();
        boolean locked = data.readBoolean();

        if (!SGCraft.hasPermission(player, "sgcraft.gui.pdd.edit")) {
            System.err.println("SGCraft - Hacked Client detected!");
            return;
        }

        final ItemStack stack = player.getHeldItemMainhand();
        if (stack != null) {
            NBTTagCompound compound = stack.getTagCompound();
            if (compound != null) {
                AddressData.updateAddress(player, compound, unid, name, address, index, locked);
                stack.setTagCompound(compound);
                player.inventoryContainer.detectAndSendChanges();
                PddNetworkHandler.updatePdd(player, false, 1);
            }
        }
    }

    public static void sendEnterSymbolToServer(SGBaseTE te, String address, int digit) {
        ChannelOutput data = pddChannel.openServer("EnterImmediateSymbol");
        writeCoords(data, te);
        data.writeUTF(address);
        data.writeInt(digit);
        data.close();
    }


    @ServerMessageHandler("EnterImmediateSymbol")
    public void handleEnterSymbolFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        String address = data.readUTF();
        int digit = data.readInt();

        SGBaseTE localGate = SGBaseTE.at(player.world, pos);
        if (localGate != null) {
            localGate.immediateDialSymbol(address, player, digit);
        }
    }
}
