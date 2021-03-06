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

import com.google.common.base.Optional;
import me.nikosgram.oglofus.protection.api.action.ActionResponse;
import me.nikosgram.oglofus.protection.api.handler.Handler;
import me.nikosgram.oglofus.protection.api.manager.RegionManager;
import me.nikosgram.oglofus.protection.api.region.ProtectionLocation;
import me.nikosgram.oglofus.protection.api.region.ProtectionRegion;
import me.nikosgram.oglofus.protection.api.region.ProtectionVector;

import java.util.*;

public class OglofusRegionManager implements RegionManager
{
    private final OglofusSponge sponge;
    private final Map< UUID, ProtectionRegion > map      = new HashMap< UUID, ProtectionRegion >();
    private final List< Handler >               handlers = new ArrayList< Handler >();

    protected OglofusRegionManager( OglofusSponge sponge )
    {
        this.sponge = sponge;
        this.sponge.getConnector().createTable(
                "oglofus_regions",
                "id int identity(1,1) primary key",
                "uuid varchar(36)",
                "name varchar(32)",
                "owner varchar(36)",
                "created date"
        );
        this.sponge.getConnector().createTable(
                "oglofus_vectors",
                "id int identity(1,1) primary key",
                "uuid varchar(36)",
                "radius tinyint",
                "x int",
                "y int",
                "z int",
                "world varchar(36)"
        );

        for ( String uid : this.sponge.getConnector().getStringList( "select uuid from oglofus_regions", "uuid" ) )
        {
            UUID uuid = UUID.fromString( uid );
            this.map.put( uuid, new OglofusProtectionRegion( uuid, this.sponge ) );
        }
    }

    @Override
    public Optional< ProtectionRegion > getRegion( UUID target )
    {
        if ( this.map.containsKey( target ) )
        {
            return Optional.of( this.map.get( target ) );
        }
        return Optional.absent();
    }

    @Override
    public Optional< ProtectionRegion > getRegion( String target )
    {
        String uid;
        if ( ( uid = this.sponge.getConnector().getString( "oglofus_regions", "name", target, "uuid" ).orNull() ) !=
                null )
        {
            UUID uuid = UUID.fromString( uid );
            if ( this.map.containsKey( uuid ) )
            {
                return Optional.of( this.map.get( uuid ) );
            }
        }
        return Optional.absent();
    }

    @Override
    public Optional< ProtectionRegion > getRegion( ProtectionLocation location )
    {
        String uid;
        if ( (
                uid = this.sponge.getConnector().getString(
                        "select uuid from oglofus_vectors where x=" +
                                location.getX() +
                                " and y=" +
                                location.getY() +
                                " and z=" +
                                location.getZ(), "uuid"
                ).orNull()
        ) != null )
        {
            UUID uuid = UUID.fromString( uid );
            if ( this.map.containsKey( uuid ) )
            {
                return Optional.of( this.map.get( uuid ) );
            }
        }
        for ( ProtectionRegion region : getRegions() )
        {
            ProtectionVector vector = region.getProtectionVector();
            if ( !location.getWorld().equals( vector.getBlockLocation().getWorld() ) )
            {
                continue;
            }
            if ( Math.abs( vector.getBlockLocation().getX() - location.getX() ) <= vector.getRadius() )
            {
                if ( Math.abs( vector.getBlockLocation().getY() - location.getY() ) <= vector.getRadius() )
                {
                    if ( Math.abs( vector.getBlockLocation().getZ() - location.getZ() ) <= vector.getRadius() )
                    {
                        return Optional.of( region );
                    }
                }
            }
        }
        return Optional.absent();
    }

    @Override
    public Collection< ProtectionRegion > getRegions()
    {
        return this.map.values();
    }

    @Override
    public ActionResponse createProtectionRegion( ProtectionLocation location, UUID owner )
    {
        //TODO create a new protection region!
        return null;
    }

    @Override
    public ActionResponse deleteProtectionRegion( ProtectionRegion area )
    {
        //TODO delete a protection region!
        return null;
    }

    @Override
    public void registerHandler( Handler handler )
    {
        this.handlers.add( handler );
    }
}
