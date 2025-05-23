package fr.openmc.core.features.city.commands;

import fr.openmc.core.OMCPlugin;
import fr.openmc.core.features.city.conditions.*;
import fr.openmc.core.features.city.listeners.CityTypeCooldown;
import fr.openmc.core.features.city.mascots.MascotUtils;
import fr.openmc.core.features.city.mascots.MascotsLevels;
import fr.openmc.core.features.city.mascots.MascotsListener;
import fr.openmc.core.features.city.mascots.MascotsManager;
import fr.openmc.core.utils.BlockVector2;
import fr.openmc.core.features.city.*;
import fr.openmc.core.features.city.menu.*;
import fr.openmc.core.features.economy.EconomyManager;
import fr.openmc.core.utils.InputUtils;
import fr.openmc.core.utils.ItemUtils;
import fr.openmc.core.utils.chronometer.Chronometer;
import fr.openmc.core.utils.chronometer.ChronometerType;
import fr.openmc.core.utils.cooldown.DynamicCooldown;
import fr.openmc.core.utils.cooldown.DynamicCooldownManager;
import fr.openmc.core.utils.customitems.CustomItemRegistry;
import fr.openmc.core.utils.database.DatabaseManager;
import fr.openmc.core.utils.menu.ConfirmMenu;
import fr.openmc.core.utils.messages.MessageType;
import fr.openmc.core.utils.messages.MessagesManager;
import fr.openmc.core.utils.messages.Prefix;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import revxrsal.commands.annotation.*;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static fr.openmc.core.features.city.CityManager.getCityType;

@Command({"ville", "city"})
public class CityCommands {
    public static HashMap<Player, Player> invitations = new HashMap<>(); // Invité, Inviteur
    public static Map<String, BukkitRunnable> balanceCooldownTasks = new HashMap<>();

    private static ItemStack ayweniteItemStack = CustomItemRegistry.getByName("omc_items:aywenite").getBest();

    private Location[] getCorners(Player player) {
        World world = player.getWorld();
        Location location = player.getLocation();
        int chunkRadius = 2;
        int chunkX = location.getChunk().getX();
        int chunkZ = location.getChunk().getZ();

        Location minLocation = new Location(world, (chunkX-chunkRadius) * 16, location.getY(), (chunkZ-chunkRadius) * 16);
        Location maxLocation = new Location(world, (chunkX+chunkRadius+1) * 16 - 1, location.getY(), (chunkZ+chunkRadius + 1) * 16 - 1);

        return new Location[]{minLocation, maxLocation};
    }

    public static int calculatePrice(int chunkCount) {
        return 5000 + (chunkCount * 1000);
    }

    public static int calculateAywenite(int chunkCount) {
        return 1*chunkCount;
    }

    @DefaultFor("~")
    void main(Player player) {
        City playerCity = CityManager.getPlayerCity(player.getUniqueId());
        if (!Chronometer.containsChronometer(player.getUniqueId(), "Mascot:chest")) {
                if (playerCity == null) {
                    NoCityMenu menu = new NoCityMenu(player);
                    menu.open();
                } else {
                    CityMenu menu = new CityMenu(player);
                    menu.open();
                }
        } else {
            MessagesManager.sendMessage(player, Component.text("Vous ne pouvez pas ouvrir le menu des villes si vous devez poser votre mascotte"), Prefix.CITY, MessageType.ERROR, false);
        }
    }

    @Subcommand("accept")
    @CommandPermission("omc.commands.city.accept")
    @Description("Accepter une invitation")
    public static void acceptInvitation(Player player) {
        //TODO: faire que le joueur peut avoir plusieurs invitations (pour eviter de bloquer le joueur concerné)
        Player inviter = invitations.get(player);
        City newCity = CityManager.getPlayerCity(inviter.getUniqueId());

        if (!CityInviteConditions.canCityInviteAccept(newCity, inviter, player)) return;

        newCity.addPlayer(player.getUniqueId());

        invitations.remove(player);

        MessagesManager.sendMessage(player, Component.text("Tu as rejoint "+ newCity.getName()), Prefix.CITY, MessageType.SUCCESS, false);
        if (inviter.isOnline()) {
            MessagesManager.sendMessage(inviter, Component.text(player.getName()+" a accepté ton invitation !"), Prefix.CITY, MessageType.SUCCESS, true);
        }
    }

    @Subcommand("rename")
    @CommandPermission("omc.commands.city.rename")
    @Description("Renommer une ville")
    void rename(Player player, @Named("nouveau nom") String name) {
        City playerCity = CityManager.getPlayerCity(player.getUniqueId());

        if (!CityManageConditions.canCityRename(playerCity, player)) return;

        if (!InputUtils.isInputCityName(name)) {
            MessagesManager.sendMessage(player, Component.text("Le nom de ville est invalide, il doit seulement comporter des caractères alphanumeriques et maximum 24 caractères."), Prefix.CITY, MessageType.ERROR, false);
            return;
        }

        playerCity.renameCity(name);
        MessagesManager.sendMessage(player, Component.text("La ville a été renommée en " + name), Prefix.CITY, MessageType.SUCCESS, false);
    }

    @Subcommand("transfer")
    @CommandPermission("omc.commands.city.transfer")
    @Description("Transfert la propriété de votre ville")
    @AutoComplete("@city_members")
    void transfer(Player sender, @Named("maire") OfflinePlayer player) {
        City playerCity = CityManager.getPlayerCity(sender.getUniqueId());

        if (!CityManageConditions.canCityTransfer(playerCity, sender)) return;

        playerCity.changeOwner(player.getUniqueId());
        MessagesManager.sendMessage(sender, Component.text("Le nouveau maire est "+player.getName()), Prefix.CITY, MessageType.SUCCESS, false);

        if (player.isOnline()) {
            MessagesManager.sendMessage((Player) player, Component.text("Vous êtes devenu le maire de la ville"), Prefix.CITY, MessageType.INFO, true);
        }
    }

    @Subcommand("deny")
    @CommandPermission("omc.commands.city.deny")
    @Description("Refuser une invitation")
    public static void denyInvitation(Player player) {
        if (!CityInviteConditions.canCityInviteDeny(player)) return;
        Player inviter = invitations.get(player);
        invitations.remove(player);

        if (inviter.isOnline()) {
            MessagesManager.sendMessage(inviter, Component.text(player.getName()+" a refusé ton invitation"), Prefix.CITY, MessageType.WARNING, true);
        }
    }

    @Subcommand("kick")
    @CommandPermission("omc.commands.city.kick")
    @Description("Exclure un habitant de votre ville")
    @AutoComplete("@city_members")
    public static void kick(Player sender, @Named("exclu") OfflinePlayer player) {
        City city = CityManager.getPlayerCity(sender.getUniqueId());

        if (!CityKickCondition.canCityKickPlayer(city, sender, player)) return;

        if (city.removePlayer(player.getUniqueId())) {
            MessagesManager.sendMessage(sender, Component.text("Tu as exclu "+player.getName()+" de la ville "+ city.getCityName()), Prefix.CITY, MessageType.SUCCESS, false);

            if (player.isOnline()) {
                MessagesManager.sendMessage((Player) player, Component.text("Tu as été exclu de la ville "+ city.getCityName()), Prefix.CITY, MessageType.INFO, true);
            }
        } else {
            MessagesManager.sendMessage(sender, Component.text("Impossible d'exclure "+player.getName()+" de la ville"), Prefix.CITY, MessageType.ERROR, false);
        }
    }

    @Subcommand("leave")
    @CommandPermission("omc.commands.city.leave")
    @Description("Quitter votre ville")
    void leave(Player player) {
        City city = CityManager.getPlayerCity(player.getUniqueId());
        if (!CityLeaveCondition.canCityLeave(city, player)) return;

        leaveCity(player);
    }

    @Subcommand("invite")
    @CommandPermission("omc.commands.city.invite")
    @Description("Inviter un joueur dans votre ville")
    public static void add(Player sender, @Named("invité") Player target) {
        City city = CityManager.getPlayerCity(sender.getUniqueId());

        if (!CityInviteConditions.canCityInvitePlayer(city, sender, target)) return;

        invitations.put(target, sender);
        MessagesManager.sendMessage(sender, Component.text("Tu as invité "+target.getName()+" dans ta ville"), Prefix.CITY, MessageType.SUCCESS, false);
        MessagesManager.sendMessage(target,
                Component.text("Tu as été invité(e) par " + sender.getName() + " dans la ville " + city.getCityName() + "\n")
                        .append(Component.text("[ACCEPTER]").color(NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/city accept")).hoverEvent(HoverEvent.showText(Component.text("Accepter l'invitation"))))
                        .append(Component.text("   "))
                                .append(Component.text("[REFUSER]").color(NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/city deny")).hoverEvent(HoverEvent.showText(Component.text("Refuser l'invitation")))),
                Prefix.CITY, MessageType.INFO, false);
    }

    @Subcommand("delete")
    @CommandPermission("omc.commands.city.delete")
    @Description("Supprimer votre ville")
    void delete(Player sender) {
        UUID uuid = sender.getUniqueId();

        City city = CityManager.getPlayerCity(uuid);

        if (!CityManageConditions.canCityDelete(city, sender)) return;

        ConfirmMenu menu = new ConfirmMenu(sender,
                () -> {
                    deleteCity(sender);
                    sender.closeInventory();
                },
                () -> {
                    sender.closeInventory();
                },
                List.of(
                        Component.text("§cEs-tu sûr de vouloir supprimer ta ville ?"),
                        Component.text("§cCette action est §4§lIRREVERSIBLE")
                ),
                List.of(
                        Component.text("§7Ne pas supprimer la ville")
                )
        );
        menu.open();
    }

    public static void deleteCity(Player sender) {
        UUID uuid = sender.getUniqueId();

        City city = CityManager.getPlayerCity(uuid);

        for (UUID townMember : city.getMembers()){
            if (Bukkit.getPlayer(townMember) instanceof Player player){
                player.clearActivePotionEffects();
            }
        }

        city.delete();
        MessagesManager.sendMessage(sender, Component.text("Votre ville a été supprimée"), Prefix.CITY, MessageType.SUCCESS, false);

        DynamicCooldownManager.use(uuid, "city:big", 60000); //1 minute
    }

    @Subcommand("claim")
    @CommandPermission("omc.commands.city.claim")
    @Description("Claim un chunk pour votre ville")
    void claim(Player sender) {
        City city = CityManager.getPlayerCity(sender.getUniqueId());

        if (!CityClaimCondition.canCityClaim(city, sender)) return;

        Chunk chunk = sender.getLocation().getChunk();

        claim(sender, chunk.getX(), chunk.getZ());
    }

    public static void claim(Player sender, int chunkX, int chunkZ) {
        City city = CityManager.getPlayerCity(sender.getUniqueId());
        org.bukkit.World bWorld = sender.getWorld();
        if (!bWorld.getName().equals("world")) {
            MessagesManager.sendMessage(sender, Component.text("Tu ne peux pas étendre ta ville ici"), Prefix.CITY, MessageType.ERROR, false);
            return;
        }

        BlockVector2 chunkVec2 = BlockVector2.at(chunkX, chunkZ);

        AtomicBoolean isFar = new AtomicBoolean(true);
        for (BlockVector2 chnk: city.getChunks()) {
            if (chnk.distance(chunkVec2) == 1) { //Si c'est en diagonale alors ça sera sqrt(2) ≈1.41
                isFar.set(false);
                break;
            }
        }

        if (isFar.get()) {
            MessagesManager.sendMessage(sender, Component.text("Ce chunk n'est pas adjacent à ta ville"), Prefix.CITY, MessageType.ERROR, false);
            return;
        }

        if (CityManager.isChunkClaimed(chunkX, chunkZ)) {
            // TODO: Vérifier si le chunk est dans le spawn
            MessagesManager.sendMessage(sender, Component.text("Ce chunk est déjà claim"), Prefix.CITY, MessageType.ERROR, false);
            return;
        }

        if (city.getChunks().size() >= 50) {
            MessagesManager.sendMessage(sender, Component.text("Ta ville est trop grande"), Prefix.CITY, MessageType.ERROR, false);
            return;
        }

        int price = calculatePrice(city.getChunks().size());
        int aywenite = calculateAywenite(city.getChunks().size());



        if (!MascotsManager.freeClaim.containsKey(city.getUUID())) {
            if (city.getBalance() < price) {
                MessagesManager.sendMessage(sender, Component.text("Ta ville n'a pas assez d'argent ("+price+EconomyManager.getEconomyIcon()+" nécessaires)"), Prefix.CITY, MessageType.ERROR, false);
                return;
            }

            if (!ItemUtils.hasEnoughItems(sender.getPlayer(), ayweniteItemStack.getType(), aywenite )) {
                MessagesManager.sendMessage(sender, Component.text("Vous n'avez pas assez d'§dAywenite §f("+aywenite+ " nécessaires)"), Prefix.CITY, MessageType.ERROR, false);
                return;
            }
        }

        if (MascotsManager.freeClaim.containsKey(city.getUUID())){
            MascotsManager.freeClaim.replace(city.getUUID(), MascotsManager.freeClaim.get(city.getUUID()) - 1);

        } else {
            city.updateBalance((double) (price*-1));
            ItemUtils.removeItemsFromInventory(sender, ayweniteItemStack.getType(), aywenite);
        }
        city.addChunk(sender.getWorld().getChunkAt(chunkX, chunkZ));

        MessagesManager.sendMessage(sender, Component.text("Ta ville a été étendue"), Prefix.CITY, MessageType.SUCCESS, false);
    }

    @Subcommand("money give")
    @CommandPermission("omc.commands.city.give")
    @Description("Transferer de l'argent vers la ville")
    void give(Player player, @Named("montant") @Range(min=1) double amount) {
        City city = CityManager.getPlayerCity(player.getUniqueId());

        if (!CityBankConditions.canCityDeposit(city, player)) return;

        if (EconomyManager.getInstance().withdrawBalance(player.getUniqueId(), amount)) {
            city.updateBalance(amount);
            MessagesManager.sendMessage(player, Component.text("Tu as transféré "+amount+EconomyManager.getEconomyIcon()+" à la ville"), Prefix.CITY, MessageType.ERROR, false);
        } else {
            MessagesManager.sendMessage(player, Component.text("Tu n'as pas assez d'argent"), Prefix.CITY, MessageType.ERROR, false);
        }
    }

    @Subcommand("money balance")
    @CommandPermission("omc.commands.city.balance")
    @Description("Afficher l'argent de votre ville")
    void balance(Player player) {
        City city = CityManager.getPlayerCity(player.getUniqueId());

        if (!CityBankConditions.canCityBalance(city, player)) return;

        double balance = city.getBalance();
        MessagesManager.sendMessage(player, Component.text(city.getCityName()+ " possède "+balance+EconomyManager.getEconomyIcon()), Prefix.CITY, MessageType.INFO, false);
    }

    @Subcommand("money take")
    @CommandPermission("omc.commands.city.take")
    @Description("Prendre de l'argent depuis votre ville")
    void take(Player player, @Named("montant") @Range(min=1) double amount) {
        City city = CityManager.getPlayerCity(player.getUniqueId());

        if (!CityBankConditions.canCityWithdraw(city, player)) return;

        if (city.getBalance() < amount) {
            MessagesManager.sendMessage(player, Component.text("Ta ville n'a pas assez d'argent en banque"), Prefix.CITY, MessageType.ERROR, false);
            return;
        }

        city.updateBalance(amount*-1);
        EconomyManager.getInstance().addBalance(player.getUniqueId(), amount);
        MessagesManager.sendMessage(player, Component.text(amount+EconomyManager.getEconomyIcon()+" ont été transférés à votre compte"), Prefix.CITY, MessageType.SUCCESS, false);
    }

    @Subcommand("info")
    @CommandPermission("omc.commands.city.info")
    @Description("Avoir des informations sur votre ville")
    void info(Player player) {
        City city = CityManager.getPlayerCity(player.getUniqueId());

        if (city == null) {
            MessagesManager.sendMessage(player, MessagesManager.Message.PLAYERNOCITY.getMessage(), Prefix.CITY, MessageType.ERROR, false);
            return;
        }

        CityMessages.sendInfo(player, city);
    }

    @Subcommand("create")
    @CommandPermission("omc.commands.city.create")
    @Description("Créer une ville")
    @DynamicCooldown(group="city:big", message = "§cTu dois attendre avant de pouvoir créer une ville (%sec% secondes)")
    void create(Player player, @Named("nom") String name) {
        if (!CityCreateConditions.canCityCreate(player)){
            MessagesManager.sendMessage(player, MessagesManager.Message.NOPERMISSION.getMessage(), Prefix.CITY, MessageType.ERROR, false);
            return;
        }


        if (!InputUtils.isInputCityName(name)) {
            MessagesManager.sendMessage(player, Component.text("Le nom de ville est invalide, il doit contenir seulement des caractères alphanumerique et doit faire moins de 24 charactères"), Prefix.CITY, MessageType.ERROR, false);
            return;
        }

        if (MascotsListener.futurCreateCity.containsKey(player.getUniqueId())){
            MessagesManager.sendMessage(player, Component.text("Vous êtes déjà entrain de créer une ville"), Prefix.CITY, MessageType.ERROR, false);
            return;
        }

        new CityTypeMenu(player, name).open();
    }
    @Subcommand("change")
    @CommandPermission("omc.commands.city.change")
    public void change(Player sender) {
        City city = CityManager.getPlayerCity(sender.getUniqueId());

        if (!CityTypeConditions.canCityChangeType(city, sender)){
            MessagesManager.sendMessage(sender, MessagesManager.Message.NOPERMISSION.getMessage(), Prefix.CITY, MessageType.ERROR, false);
            return;
        }

        String cityTypeActuel = getCityType(city.getUUID());
        String cityTypeAfter = "";
        if (cityTypeActuel != null) {
            cityTypeActuel = cityTypeActuel.equals("war") ? "§cen guerre§7" : "§aen paix§7";
            cityTypeAfter = cityTypeActuel.equals("war") ? "§aen paix§7" : "§cen guerre§7";
        }

        ConfirmMenu menu = new ConfirmMenu(sender,
                () -> {
                    changeConfirm(sender);
                    sender.closeInventory();
                },
                () -> {
                    sender.closeInventory();
                },
                List.of(
                        Component.text("§cEs-tu sûr de vouloir changer le type de ta §dville §7?"),
                        Component.text("§7Vous allez passez d'une §dville " + cityTypeActuel + " à une §dville " + cityTypeAfter),
                        Component.text("§cSi tu fais cela ta mascotte §4§lPERDERA 2 NIVEAUX")
                ),
                List.of(
                        Component.text("§7Ne pas changer le type de ta §dville")
                )
        );
        menu.open();

    }

    public static void changeConfirm(Player sender) {
        City city = CityManager.getPlayerCity(sender.getUniqueId());

        if (!CityTypeConditions.canCityChangeType(city, sender)){
            MessagesManager.sendMessage(sender, MessagesManager.Message.NOPERMISSION.getMessage(), Prefix.CITY, MessageType.ERROR, false);
            return;
        }

        if (MascotUtils.mascotsContains(city.getUUID())) {
            if (!MascotUtils.getMascotState(city.getUUID())) {
                MessagesManager.sendMessage(sender, Component.text("Vous devez soigner votre mascotte avant"), Prefix.CITY, MessageType.ERROR, false);
                return;
            }
        }

        if (CityTypeCooldown.isOnCooldown(city.getUUID())) {
            MessagesManager.sendMessage(sender, Component.text("Vous devez attendre " + CityTypeCooldown.getRemainingCooldown(city.getUUID()) / 1000 + " seconds pour changer de type de ville"), Prefix.CITY, MessageType.ERROR, false);
            return;
        }
        CityManager.changeCityType(city.getUUID());
        CityTypeCooldown.setCooldown(city.getUUID());

        if (MascotUtils.getMascotUUIDOfCity(city.getUUID()) != null) {
            LivingEntity mob = (LivingEntity) Bukkit.getEntity(MascotUtils.getMascotUUIDOfCity(city.getUUID()));
            MascotsLevels mascotsLevels = MascotsLevels.valueOf("level" + MascotUtils.getMascotLevel(city.getUUID()));

            for (UUID townMember : city.getMembers()) {
                if (Bukkit.getPlayer(townMember) instanceof Player player) {
                    for (PotionEffect potionEffect : mascotsLevels.getBonus()) {
                        player.removePotionEffect(potionEffect.getType());
                    }
                    MascotsManager.giveMascotsEffect(city.getUUID(), player.getUniqueId());
                }
            }

            double lastHealth = mascotsLevels.getHealth();
            int newLevel = Integer.parseInt(String.valueOf(mascotsLevels).replaceAll("[^0-9]", "")) - 2;
            if (newLevel < 1) {
                newLevel = 1;
            }
            MascotUtils.setMascotLevel(city.getUUID(), newLevel);
            mascotsLevels = MascotsLevels.valueOf("level" + MascotUtils.getMascotLevel(city.getUUID()));

            try {
                int maxHealth = mascotsLevels.getHealth();
                mob.setMaxHealth(maxHealth);
                if (mob.getHealth() >= lastHealth) {
                    mob.setHealth(maxHealth);
                }
                double currentHealth = mob.getHealth();
                mob.setCustomName("§lMascotte §c" + currentHealth + "/" + maxHealth + "❤");
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    // ACTIONS

    public static boolean createCity(Player player, String name, String type) {

        UUID uuid = player.getUniqueId();

        MessagesManager.sendMessage(player, Component.text("Votre ville est en cours de création..."), Prefix.CITY, MessageType.INFO, false);

        String cityUUID = UUID.randomUUID().toString().substring(0, 8);

        Chunk origin = player.getChunk();
        AtomicBoolean isClaimed = new AtomicBoolean(false);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (CityManager.isChunkClaimed(origin.getX() + x, origin.getZ() + z)) {
                    isClaimed.set(true);
                    break;
                }
            }
        }

        if (isClaimed.get()) {
            MessagesManager.sendMessage(player, Component.text("Cette parcelle est déjà claim"), Prefix.CITY, MessageType.ERROR, false);
            return false;
        }

        Bukkit.getScheduler().runTaskAsynchronously(OMCPlugin.getInstance(), () -> {
            try {
                PreparedStatement statement = DatabaseManager.getConnection().prepareStatement("INSERT INTO city_regions (city_uuid, x, z) VALUES (?, ?, ?)");
                statement.setString(1, cityUUID);

                statement.setInt(2, origin.getX());
                statement.setInt(3, origin.getZ());
                statement.addBatch();

                statement.executeBatch();
                statement.close();
            } catch (SQLException e) {
                MessagesManager.sendMessage(player, Component.text("Une erreur est survenue, réessayez plus tard"), Prefix.CITY, MessageType.ERROR, false);
                throw new RuntimeException(e);
            }
        });

        // innutile de recheck si le joueur a assez de ressource vu qu'on check dans CityCreateConditions
        EconomyManager.getInstance().withdrawBalance(player.getUniqueId(), CityCreateConditions.MONEY_CREATE);
        ItemUtils.removeItemsFromInventory(player, ayweniteItemStack.getType(), CityCreateConditions.AYWENITE_CREATE);

        City city = CityManager.createCity(player, cityUUID, name, type);
        city.addPlayer(uuid);
        city.addPermission(uuid, CPermission.OWNER);

        CityManager.claimedChunks.put(BlockVector2.at(origin.getX(), origin.getZ()), city);
        MascotsManager.freeClaim.put(cityUUID, 15);

        player.closeInventory();

        MessagesManager.sendMessage(player, Component.text("Votre ville a été créée : " + name), Prefix.CITY, MessageType.SUCCESS, true);
        MessagesManager.sendMessage(player, Component.text("Vous disposez de 15 claims gratuits"), Prefix.CITY, MessageType.SUCCESS, false);

        DynamicCooldownManager.use(uuid, "city:big", 60000); //1 minute

        return true;
    }

    public static void leaveCity(Player player) {
        City city = CityManager.getPlayerCity(player.getUniqueId());
        if (city.removePlayer(player.getUniqueId())) {
            MessagesManager.sendMessage(player, Component.text("Tu as quitté "+ city.getCityName()), Prefix.CITY, MessageType.SUCCESS, false);
        } else {
            MessagesManager.sendMessage(player, Component.text("Impossible de quitter la ville"), Prefix.CITY, MessageType.ERROR, false);
        }
    }

    public static void startBalanceCooldown(String city_uuid) {
        if (balanceCooldownTasks.containsKey(city_uuid)) {
            balanceCooldownTasks.get(city_uuid).cancel();
        }

        BukkitRunnable cooldownTask = new BukkitRunnable() {
            @Override
            public void run() {
                balanceCooldownTasks.remove(city_uuid);
            }
        };

        balanceCooldownTasks.put(city_uuid, cooldownTask);
        cooldownTask.runTaskLater(OMCPlugin.getInstance(), 30 * 60 * 20L);
    }
}
