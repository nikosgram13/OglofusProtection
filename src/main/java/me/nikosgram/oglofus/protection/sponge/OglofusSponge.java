/*
 * Copyright 2014-2015 Nikos Grammatikos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://raw.githubusercontent.com/nikosgram13/OglofusProtection/master/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.nikosgram.oglofus.protection.sponge;

import com.google.inject.Inject;
import lombok.Getter;
import me.nikosgram.oglofus.database.DatabaseConnector;
import me.nikosgram.oglofus.database.MySQLDatabaseDriver;
import me.nikosgram.oglofus.database.SQLiteDatabaseDriver;
import me.nikosgram.oglofus.protection.api.OglofusProtection;
import me.nikosgram.oglofus.protection.api.action.ActionResponse;
import me.nikosgram.oglofus.protection.api.manager.InvitationManager;
import me.nikosgram.oglofus.protection.api.manager.RegionManager;
import me.nikosgram.oglofus.protection.api.plugin.ProtectionPlugin;
import me.nikosgram.oglofus.protection.api.region.ProtectionLocation;
import me.nikosgram.oglofus.protection.api.region.ProtectionRegion;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.Server;
import org.spongepowered.api.entity.player.Player;
import org.spongepowered.api.event.Subscribe;
import org.spongepowered.api.event.entity.player.PlayerBreakBlockEvent;
import org.spongepowered.api.event.entity.player.PlayerInteractBlockEvent;
import org.spongepowered.api.event.entity.player.PlayerPlaceBlockEvent;
import org.spongepowered.api.event.state.InitializationEvent;
import org.spongepowered.api.event.state.PreInitializationEvent;
import org.spongepowered.api.event.state.ServerStoppedEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.config.DefaultConfig;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.command.CommandException;
import org.spongepowered.api.util.command.CommandPermissionException;
import org.spongepowered.api.util.command.CommandResult;
import org.spongepowered.api.util.command.CommandSource;
import org.spongepowered.api.util.command.args.CommandContext;
import org.spongepowered.api.util.command.args.GenericArguments;
import org.spongepowered.api.util.command.spec.CommandExecutor;
import org.spongepowered.api.util.command.spec.CommandSpec;
import org.spongepowered.api.world.World;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Plugin( id = "OglofusProtection", name = "OglofusProtection", version = "2.0.1-R0.1-SNAPSHOT" )
public class OglofusSponge implements ProtectionPlugin
{
    @Getter
    @Inject
    private Game                                              game;
    @Getter
    private Server                                            server;
    @Getter
    @Inject
    private Logger                                            logger;
    @Getter
    @Inject
    @DefaultConfig( sharedRoot = true )
    private File                                              configFile;
    @Getter
    @Inject
    @DefaultConfig( sharedRoot = true )
    private ConfigurationLoader< CommentedConfigurationNode > configManager;
    @Getter
    private ConfigurationNode                                 config;
    @Getter
    private DatabaseConnector                                 connector;
    @Getter
    private RegionManager                                     regionManager;
    @Getter
    private InvitationManager                                 invitationManager;

    public OglofusSponge()
    {
        OglofusProtection.invoke( this );
    }

    @Subscribe
    public void onPreInitialization( PreInitializationEvent event )
    {
        this.server = this.game.getServer();
        try
        {
            if ( !this.configFile.exists() )
            {
                Files.createFile( this.configFile.toPath() );
                this.config = this.configManager.load();

                this.config.getNode( "ConfigVersion" ).setValue( 1 );

                this.config.getNode( "database", "type" ).setValue( "mysql" );
                this.config.getNode( "database", "host" ).setValue( "localhost" );
                this.config.getNode( "database", "port" ).setValue( 3306 );
                this.config.getNode( "database", "user" ).setValue( "root" );
                this.config.getNode( "database", "pass" ).setValue( "password" );
                this.config.getNode( "database", "data" ).setValue( "database" );

                this.config.getNode( "protection", "material" ).setValue( "SPONGE" );
                this.config.getNode( "protection", "metadata" ).setValue( "protector" );

                this.configManager.save( config );
                this.logger.info(
                        "Created default configuration, " +
                                "OglofusProtection will not run until you have edited this file!"
                );
            }
        } catch ( IOException exception )
        {
            this.logger.error( "Couldn't create default configuration file!" );
        }

        if ( this.config.getNode( "database", "type" ).getString().equalsIgnoreCase( "sqlite" ) )
        {
            this.connector = new DatabaseConnector(
                    new SQLiteDatabaseDriver(
                            Paths.get(
                                    this.config.getNode(
                                            "database", "host"
                                    ).getString()
                            )
                    )
            );
        } else
        {
            this.connector = new DatabaseConnector(
                    new MySQLDatabaseDriver(
                            this.config.getNode( "database", "user" ).getString(),
                            this.config.getNode( "database", "data" ).getString(),
                            this.config.getNode( "database", "pass" ).getString(),
                            this.config.getNode( "database", "host" ).getString(),
                            this.config.getNode( "database", "port" ).getInt()
                    )
            );
        }

        this.connector.openConnection();

        if ( this.connector.checkConnection() )
        {
            this.regionManager = new OglofusRegionManager( this );
            this.invitationManager = new OglofusInvitationManager( this );
        }
    }

    @Subscribe
    public void onInitialization( InitializationEvent event )
    {
        CommandSpec info = CommandSpec.builder().permission( "oglofus.protection.command.info" ).description(
                Texts.of( "Display the info from your region." )
        ).arguments(
                GenericArguments.optional( GenericArguments.string( Texts.of( "region" ) ) )
        ).executor(
                new CommandExecutor()
                {
                    @Override
                    public CommandResult execute( CommandSource src, CommandContext args ) throws CommandException
                    {
                        //TODO
                        return null;
                    }
                }
        ).build();

        CommandSpec invite = CommandSpec.builder().permission( "oglofus.protection.command.invite" ).description(
                Texts.of( "Invite a player to your region." )
        ).arguments(
                GenericArguments.onlyOne( GenericArguments.player( Texts.of( "player" ), this.game ) )
        ).executor(
                new CommandExecutor()
                {
                    @Override
                    public CommandResult execute( CommandSource src, CommandContext args ) throws CommandException
                    {
                        //TODO
                        return null;
                    }
                }
        ).build();

        CommandSpec accept = CommandSpec.builder().permission( "oglofus.protection.command.accept" ).description(
                Texts.of( "Accept a invitation." )
        ).arguments(
                GenericArguments.optional( GenericArguments.string( Texts.of( "region" ) ) )
        ).executor(
                new CommandExecutor()
                {
                    @Override
                    public CommandResult execute( CommandSource src, CommandContext args ) throws CommandException
                    {
                        //TODO
                        return null;
                    }
                }
        ).build();

        CommandSpec kick = CommandSpec.builder().permission( "oglofus.protection.command.kick" ).description(
                Texts.of( "Kick a player from your region." )
        ).arguments(
                GenericArguments.onlyOne( GenericArguments.player( Texts.of( "player" ), this.game ) )
        ).executor(
                new CommandExecutor()
                {
                    @Override
                    public CommandResult execute( CommandSource src, CommandContext args ) throws CommandException
                    {
                        if ( src instanceof Player )
                        {
                            Player player = ( Player ) src;
                            ProtectionRegion region = OglofusSponge.this.regionManager.getRegion(
                                    new OglofusProtectionLocation(
                                            OglofusSponge.this, player.getLocation()
                                    )
                            ).orNull();
                            if ( region == null )
                            {
                                throw new CommandException(
                                        Texts.builder( "You are not in some region." ).color(
                                                TextColors.RED
                                        ).build()
                                );
                            } else
                            {
                                ActionResponse response = region.getProtectionStaff().kick(
                                        src, args.< Player >getOne( "player" ).get().getUniqueId()
                                );
                                if ( response.equals( ActionResponse.Successful ) )
                                {
                                    player.sendMessage(
                                            Texts.builder( "You have kick the player '" ).append(
                                                    args.< Player >getOne(
                                                            "player"
                                                    ).get().getDisplayNameData().getDisplayName()
                                            ).append(
                                                    Texts.builder( "' from this region." ).color(
                                                            TextColors.GRAY
                                                    ).build()
                                            ).color(
                                                    TextColors.GRAY
                                            ).build()
                                    );
                                } else
                                {
                                    switch ( response.getMessage() )
                                    {
                                        case "access":
                                            throw new CommandException(
                                                    Texts.builder( "You don't have access to this region." ).color(
                                                            TextColors.RED
                                                    ).build()
                                            );
                                            //TODO: make the promote kick in ProtectionStaff.
                                    }
                                }
                            }
                        } else
                        {
                            throw new CommandException( Texts.builder( "I don't know who are you!" ).color( TextColors.RED ).build() );
                        }
                        return CommandResult.success();
                    }
                }
        ).build();

        CommandSpec promote = CommandSpec.builder().permission( "oglofus.protection.command.promote" ).description(
                Texts.of( "Promote a player from member to officer in your region." )
        ).arguments(
                GenericArguments.onlyOne( GenericArguments.player( Texts.of( "player" ), this.game ) )
        ).executor(
                new CommandExecutor()
                {
                    @Override
                    public CommandResult execute( CommandSource src, CommandContext args ) throws CommandException
                    {
                        if ( src instanceof Player )
                        {
                            Player player = ( Player ) src;
                            ProtectionRegion region = OglofusSponge.this.regionManager.getRegion(
                                    new OglofusProtectionLocation(
                                            OglofusSponge.this, player.getLocation()
                                    )
                            ).orNull();
                            if ( region == null )
                            {
                                throw new CommandException(
                                        Texts.builder( "You are not in some region." ).color(
                                                TextColors.RED
                                        ).build()
                                );
                            } else
                            {
                                ActionResponse response = region.getProtectionStaff().promote(
                                        src, args.< Player >getOne( "player" ).get().getUniqueId()
                                );
                                if ( response.equals( ActionResponse.Successful ) )
                                {
                                    player.sendMessage(
                                            Texts.builder( "You have promote the player '" ).append(
                                                    args.< Player >getOne(
                                                            "player"
                                                    ).get().getDisplayNameData().getDisplayName()
                                            ).append( Texts.builder( "' to officer." ).color( TextColors.GRAY ).build() ).color(
                                                    TextColors.GRAY
                                            ).build()
                                    );
                                } else
                                {
                                    switch ( response.getMessage() )
                                    {
                                        case "access":
                                            throw new CommandException(
                                                    Texts.builder( "You don't have access to this region." ).color(
                                                            TextColors.RED
                                                    ).build()
                                            );
                                            //TODO: make the promote method in ProtectionStaff.
                                    }
                                }
                            }
                        } else
                        {
                            throw new CommandException( Texts.builder( "I don't know who are you!" ).color( TextColors.RED ).build() );
                        }
                        return CommandResult.success();
                    }
                }
        ).build();

        final CommandSpec demote = CommandSpec.builder().permission( "oglofus.protection.command.demote" ).description(
                Texts.of( "Demote a player from officer to member in your region." )
        ).arguments(
                GenericArguments.onlyOne( GenericArguments.player( Texts.of( "player" ), this.game ) )
        ).executor(
                new CommandExecutor()
                {
                    @Override
                    public CommandResult execute( CommandSource src, CommandContext args ) throws CommandException
                    {
                        if ( src instanceof Player )
                        {
                            Player player = ( Player ) src;
                            ProtectionRegion region = OglofusSponge.this.regionManager.getRegion(
                                    new OglofusProtectionLocation(
                                            OglofusSponge.this, player.getLocation()
                                    )
                            ).orNull();
                            if ( region == null )
                            {
                                throw new CommandException(
                                        Texts.builder( "You are not in some region." ).color(
                                                TextColors.RED
                                        ).build()
                                );
                            } else
                            {
                                ActionResponse response = region.getProtectionStaff().demote(
                                        src, args.< Player >getOne( "player" ).get().getUniqueId()
                                );
                                if ( response.equals( ActionResponse.Successful ) )
                                {
                                    player.sendMessage(
                                            Texts.builder( "You have demote the player '" ).append(
                                                    args.< Player >getOne(
                                                            "player"
                                                    ).get().getDisplayNameData().getDisplayName()
                                            ).append( Texts.builder( "' to member." ).color( TextColors.GRAY ).build() ).color(
                                                    TextColors.GRAY
                                            ).build()
                                    );
                                } else
                                {
                                    switch ( response.getMessage() )
                                    {
                                        case "access":
                                            throw new CommandException(
                                                    Texts.builder( "You don't have access to this region." ).color(
                                                            TextColors.RED
                                                    ).build()
                                            );
                                            //TODO: make the demote method in ProtectionStaff.
                                    }
                                }
                            }
                        } else
                        {
                            throw new CommandException( Texts.builder( "I don't know who are you!" ).color( TextColors.RED ).build() );
                        }
                        return CommandResult.success();
                    }
                }
        ).build();

        CommandSpec give = CommandSpec.builder().permission( "oglofus.protection.command.give" ).description(
                Texts.of( "Give to you a protection block." )
        ).arguments(
                GenericArguments.optional( GenericArguments.integer( Texts.of( "amount" ) ) ),
                GenericArguments.optional( GenericArguments.player( Texts.of( "player" ), this.game ) )
        ).executor(
                new CommandExecutor()
                {
                    @Override
                    public CommandResult execute( CommandSource src, CommandContext args ) throws CommandException
                    {
                        Player player;
                        if ( args.hasAny( "player" ) )
                        {
                            if ( src instanceof Player )
                            {
                                player = ( Player ) src;
                            } else
                            {
                                throw new CommandException(
                                        Texts.builder( "I don't know who are you!" ).color(
                                                TextColors.RED
                                        ).build()
                                );
                            }
                        } else
                        {
                            if ( !src.hasPermission( "oglofus.sponge.gamemode.others" ) )
                            {
                                throw new CommandPermissionException(
                                        Texts.builder( "You do not have permission to use this command!" ).color(
                                                TextColors.RED
                                        ).build()
                                );
                            }
                            player = args.< Player >getOne( "player" ).get();
                        }
                        //TODO: waiting for getType method in ItemTypes.
                        return CommandResult.success();
                    }
                }
        ).build();

        CommandSpec rename = CommandSpec.builder().permission( "oglofus.protection.command.rename" ).description(
                Texts.of( "Rename your region." )
        ).arguments(
                GenericArguments.onlyOne( GenericArguments.string( Texts.of( "name" ) ) )
        ).executor(
                new CommandExecutor()
                {
                    @Override
                    public CommandResult execute( CommandSource src, CommandContext args ) throws CommandException
                    {
                        if ( src instanceof Player )
                        {
                            Player player = ( Player ) src;
                            ProtectionRegion region = OglofusSponge.this.regionManager.getRegion(
                                    new OglofusProtectionLocation(
                                            OglofusSponge.this, player.getLocation()
                                    )
                            ).orNull();
                            if ( region == null )
                            {
                                throw new CommandException(
                                        Texts.builder( "You are not in some region." ).color(
                                                TextColors.RED
                                        ).build()
                                );
                            } else
                            {
                                ActionResponse response = region.changeName(
                                        src, args.< String >getOne( "name" ).get()
                                );
                                if ( response.equals( ActionResponse.Successful ) )
                                {
                                    player.sendMessage(
                                            Texts.builder( "You have change the region's name to '" ).append(
                                                    Texts.builder(
                                                            region.getName()
                                                    ).color( TextColors.RED ).build()
                                            ).append( Texts.builder( "'." ).color( TextColors.GRAY ).build() ).color(
                                                    TextColors.GRAY
                                            ).build()
                                    );
                                } else
                                {
                                    switch ( response.getMessage() )
                                    {
                                        case "access":
                                            throw new CommandException(
                                                    Texts.builder( "You don't have access to this region." ).color(
                                                            TextColors.RED
                                                    ).build()
                                            );
                                        case "length":
                                            throw new CommandException(
                                                    Texts.builder(
                                                            "The name is too big. Please type a name smaller from 36 chars."
                                                    ).color(
                                                            TextColors.RED
                                                    ).build()
                                            );
                                        case "exists":
                                            throw new CommandException(
                                                    Texts.builder( "Some other region has this name." ).color(
                                                            TextColors.RED
                                                    ).build()
                                            );
                                    }
                                }
                            }
                        } else
                        {
                            throw new CommandException( Texts.builder( "I don't know who are you!" ).color( TextColors.RED ).build() );
                        }
                        return CommandResult.success();
                    }
                }
        ).build();

        CommandSpec protection = CommandSpec.builder().permission( "oglofus.protection.command" ).description(
                Texts.of( "Access to protection command." )
        ).child( info, "info", "i", "here" ).child( invite, "invite", "inv" ).child(
                accept, "accept", "acc"
        ).child( kick, "kick" ).child( promote, "promote", "pro" ).child(
                demote, "demote", "dem"
        ).child( give, "give" ).child( rename, "rename", "changename", "setname" ).build();

        this.game.getCommandDispatcher().register( this, protection, "protection", "protector", "protect", "p" );
    }

    @Subscribe
    public void onServerStopped( ServerStoppedEvent event )
    {
        this.connector.closeConnection();
    }

    @Subscribe
    public void security( PlayerInteractBlockEvent event )
    {
        ProtectionLocation location = new OglofusProtectionLocation(
                this,
                ( ( World ) event.getBlock().getExtent() ).getUniqueId(),
                event.getBlock().getBlockX(),
                event.getBlock().getBlockY(),
                event.getBlock().getBlockZ()
        );
        ProtectionRegion region;
        if ( ( region = getRegionManager().getRegion( location ).orNull() ) != null )
        {
            if ( !region.getProtectionStaff().hasMemberAccess( event.getEntity().getUniqueId() ) )
            {
                event.setCancelled( true );
            } else
            {
                if ( region.getProtectionVector().getBlockLocation().equals( location ) )
                {
                    if ( !region.getProtectionStaff().hasOwnerAccess( event.getEntity().getUniqueId() ) )
                    {
                        event.setCancelled( true );
                    }
                }
            }
        }
    }

    @Subscribe
    public void security( PlayerPlaceBlockEvent event )
    {
        ProtectionLocation location = new OglofusProtectionLocation(
                this,
                ( ( World ) event.getBlock().getExtent() ).getUniqueId(),
                event.getBlock().getBlockX(),
                event.getBlock().getBlockY(),
                event.getBlock().getBlockZ()
        );
        ProtectionRegion region;
        if ( ( region = getRegionManager().getRegion( location ).orNull() ) != null )
        {
            if ( !region.getProtectionStaff().hasMemberAccess( event.getEntity().getUniqueId() ) )
            {
                event.setCancelled( true );
            } else
            {
                if ( region.getProtectionVector().getBlockLocation().equals( location ) )
                {
                    if ( !region.getProtectionStaff().hasOwnerAccess( event.getEntity().getUniqueId() ) )
                    {
                        event.setCancelled( true );
                    }
                }
            }
        }
    }

    @Subscribe
    public void security( PlayerBreakBlockEvent event )
    {
        ProtectionLocation location = new OglofusProtectionLocation(
                this,
                ( ( World ) event.getBlock().getExtent() ).getUniqueId(),
                event.getBlock().getBlockX(),
                event.getBlock().getBlockY(),
                event.getBlock().getBlockZ()
        );
        ProtectionRegion region;
        if ( ( region = getRegionManager().getRegion( location ).orNull() ) != null )
        {
            if ( !region.getProtectionStaff().hasMemberAccess( event.getEntity().getUniqueId() ) )
            {
                event.setCancelled( true );
            } else
            {
                if ( region.getProtectionVector().getBlockLocation().equals( location ) )
                {
                    if ( !region.getProtectionStaff().hasOwnerAccess( event.getEntity().getUniqueId() ) )
                    {
                        event.setCancelled( true );
                    }
                }
            }
        }
    }
}
