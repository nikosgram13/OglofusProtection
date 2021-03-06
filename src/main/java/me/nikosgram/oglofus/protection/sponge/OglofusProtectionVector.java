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

import com.flowpowered.math.vector.Vector3d;
import lombok.Getter;
import me.nikosgram.oglofus.protection.api.region.ProtectionLocation;
import me.nikosgram.oglofus.protection.api.region.ProtectionVector;
import me.nikosgram.oglofus.utils.OglofusUtils;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class OglofusProtectionVector implements ProtectionVector
{
    private final OglofusSponge      sponge;
    @Getter
    private final int                radius;
    @Getter
    private final ProtectionLocation blockLocation;
    @Getter
    private final ProtectionLocation minLocation;
    @Getter
    private final ProtectionLocation maxLocation;

    public OglofusProtectionVector( OglofusSponge sponge, int radius, ProtectionLocation blockLocation )
    {
        this.sponge = sponge;
        this.radius = radius;
        this.blockLocation = blockLocation;
        this.minLocation = new OglofusProtectionLocation( sponge, getBlockLocation() ).add( -radius, -radius, -radius );
        this.maxLocation = new OglofusProtectionLocation( sponge, getBlockLocation() ).add( radius, radius, radius );
    }

    protected OglofusProtectionVector( UUID uuid, OglofusSponge sponge )
    {
        this.sponge = sponge;
        this.radius = ( int ) this.sponge.getConnector().getObject(
                "oglofus_vectors", "uuid", uuid.toString(), "radius"
        ).get();
        this.blockLocation = new OglofusProtectionLocation(
                this.sponge,
                UUID.fromString(
                        this.sponge.getConnector().getString(
                                "oglofus_vectors", "uuid", uuid.toString(), "world"
                        ).get()
                ),
                ( int ) sponge.getConnector().getObject( "oglofus_vectors", "uuid", uuid.toString(), "x" ).get(),
                ( int ) sponge.getConnector().getObject( "oglofus_vectors", "uuid", uuid.toString(), "y" ).get(),
                ( int ) sponge.getConnector().getObject( "oglofus_vectors", "uuid", uuid.toString(), "z" ).get()
        );
        this.minLocation = new OglofusProtectionLocation( sponge, getBlockLocation() ).add(
                -this.radius, -this.radius, -this.radius
        );
        this.maxLocation = new OglofusProtectionLocation( sponge, getBlockLocation() ).add(
                this.radius, this.radius, this.radius
        );
    }

    @Override
    public < T > Collection< T > getBlocks( Class< T > tClass )
    {
        List< T > returned = new ArrayList< T >();
        for ( int location_x = this.minLocation.getX(); location_x <= this.maxLocation.getX(); location_x++ )
        {
            for ( int location_y = this.minLocation.getY(); location_y <= this.maxLocation.getY(); location_y++ )
            {
                for ( int location_z = this.minLocation.getZ(); location_z <= this.maxLocation.getZ(); location_z++ )
                {
                    Location location = new Location(
                            this.blockLocation.getWorldAs( World.class ).get(), location_x, location_y, location_z
                    );
                    if ( OglofusUtils.equalClass( tClass, Location.class ) )
                    {
                        returned.add( ( T ) location );
                    } else
                        if ( OglofusUtils.equalClass( tClass, BlockState.class ) )
                        {
                            returned.add( ( T ) location.getState() );
                        } else
                            if ( OglofusUtils.equalClass( tClass, Vector3d.class ) )
                            {
                                returned.add( ( T ) location.getPosition() );
                            }
                }
            }
        }
        return returned;
    }

    @Override
    public < T > Collection< T > getEntities( Class< T > tClass )
    {
        List< T > returned = new ArrayList< T >();
        if ( OglofusUtils.equalClass( tClass, Entity.class ) )
        {
            for ( Entity entity : this.blockLocation.getWorldAs( World.class ).get().getEntities() )
            {
                if ( Math.abs( this.blockLocation.getX() - entity.getLocation().getX() ) <= this.radius )
                {
                    if ( Math.abs( this.blockLocation.getY() - entity.getLocation().getY() ) <= this.radius )
                    {
                        if ( Math.abs( this.blockLocation.getZ() - entity.getLocation().getZ() ) <= this.radius )
                        {
                            returned.add( ( T ) entity );
                        }
                    }
                }
            }
        }
        return returned;
    }
}
