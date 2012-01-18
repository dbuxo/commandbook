// $Id$
/*
 * Copyright (C) 2010, 2011 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.sk89q.commandbook.locations;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.sk89q.commandbook.CommandBook;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import static com.sk89q.commandbook.CommandBookUtil.getNestedList;
import static com.sk89q.commandbook.CommandBook.logger;

public class FlatFileLocationsManager implements LocationManager<NamedLocation> {
    
    private World castWorld;
    private File file;
    private Map<String, NamedLocation> locs = new HashMap<String, NamedLocation>();
    private Map<String, List<NamedLocation>> unloadedLocs = new HashMap<String, List<NamedLocation>>();
    private String type;
    
    /**
     * Construct the manager.
     * 
     * @param file
     * @param type
     */
    public FlatFileLocationsManager(File file, String type) {
        this.file = file;
        this.type = type;
    }

    public void castWorld(World world) {
        castWorld = world;
    }
    
    public void load() throws IOException {
        FileInputStream input = null;
        Map<String, NamedLocation> locs = new HashMap<String, NamedLocation>();
        
        file.getParentFile().mkdirs();
        if (!file.exists()) {
            file.createNewFile();
        }

        try {
            input = new FileInputStream(file);
            InputStreamReader streamReader = new InputStreamReader(input, "utf-8");
            BufferedReader reader = new BufferedReader(streamReader);
            
            CSVReader csv = new CSVReader(reader);
            String[] line;
            while ((line = csv.readNext()) != null) {
                if (line.length < 7) {
                    logger().warning(type + " data file has an invalid line with < 7 fields");
                } else {
                    try {
                        String name = line[0].trim().replace(" ", "");
                        String worldName = line[1]; // Set to null if the world exists
                        String creator = line[2];
                        double x = Double.parseDouble(line[3]);
                        double y = Double.parseDouble(line[4]);
                        double z = Double.parseDouble(line[5]);
                        float pitch = Float.parseFloat(line[6]);
                        float yaw = Float.parseFloat(line[7]);
                        
                        World world = CommandBook.server().getWorld(worldName);
                        
                      
                        if (world != null) {
                            // We shouldn't have this warp
                            if (castWorld != null && !castWorld.equals(world)) {
                                continue;
                            }
                        }
                        
                        Location loc = new Location(world, x, y, z, yaw, pitch);
                        NamedLocation warp = new NamedLocation(name, loc);
                        warp.setWorldName(worldName);
                        warp.setCreatorName(creator);
                        if (world == null) {
                            getNestedList(unloadedLocs, worldName).add(warp);
                        } else {
                            locs.put(name.toLowerCase(), warp);
                        }
                    } catch (NumberFormatException e) {
                        logger().warning(type + " data file has an invalid line with non-numeric numeric fields");
                    }
                }
            }
            
            this.locs = locs;
            
            if (castWorld != null) {
                logger().info(locs.size() + " " + type + "(s) loaded for "
                        + castWorld.getName());
            } else {
                logger().info(locs.size() + " " + type + "(s) loaded");
            }
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public void save() throws IOException {
        FileOutputStream output = null;
        
        try {
            output = new FileOutputStream(file);
            OutputStreamWriter streamWriter = new OutputStreamWriter(output, "utf-8");
            BufferedWriter writer = new BufferedWriter(streamWriter);
            
            CSVWriter csv = new CSVWriter(writer);
            
            synchronized (this) {
                for (Map.Entry<String, NamedLocation> entry : locs.entrySet()) {
                    NamedLocation warp = entry.getValue();
                    
                    csv.writeNext(new String[] {
                            warp.getName(),
                            warp.getWorldName() != null ? warp.getWorldName()
                                    : warp.getLocation().getWorld().getName(),
                            warp.getCreatorName(),
                            String.valueOf(warp.getLocation().getX()),
                            String.valueOf(warp.getLocation().getY()),
                            String.valueOf(warp.getLocation().getZ()),
                            String.valueOf(warp.getLocation().getPitch()),
                            String.valueOf(warp.getLocation().getYaw()),
                            });
                }
            }
            
            csv.flush();
            csv.close();
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public void updateWorlds() {
        for (Iterator<Map.Entry<String, List<NamedLocation>>> i = unloadedLocs.entrySet().iterator(); i.hasNext();) {
            Map.Entry<String, List<NamedLocation>> entry = i.next();
            World world = CommandBook.server().getWorld(entry.getKey());
            if (world == null) continue;
            i.remove();
            for (NamedLocation warp : entry.getValue()) {
                warp.getLocation().setWorld(world);
                locs.put(warp.getName().toLowerCase(), warp);
            }
        }
        for (Iterator<NamedLocation> i = locs.values().iterator(); i.hasNext();) {
            NamedLocation loc = i.next();
            if (CommandBook.server().getWorld(loc.getWorldName()) == null) {
                i.remove();
                getNestedList(unloadedLocs, loc.getWorldName()).add(loc);
            }
        }
    }

    public NamedLocation get(String id) {
        return locs.get(id.toLowerCase());
    }

    public boolean remove(String id) {
        return locs.remove(id.toLowerCase()) != null;
    }

    public List<NamedLocation> getLocations() {
        return new ArrayList<NamedLocation>(locs.values());
    }

    public NamedLocation create(String id, Location loc, Player player) {
        id = id.trim();
        NamedLocation warp = new NamedLocation(id, loc);
        locs.put(id.toLowerCase(), warp);
        if (player != null) {
            warp.setCreatorName(player.getName());
        } else {
            warp.setCreatorName("");
        }
        return warp;
    }

    public static class LocationsFactory implements LocationManagerFactory<LocationManager<NamedLocation>> {

        public File rootDir;
        private String type;

        public LocationsFactory(File rootDir, String type) {
            this.rootDir = rootDir;
            this.type = type;
        }

        public LocationManager<NamedLocation> createManager() {
            return new FlatFileLocationsManager(new File(rootDir, type.toLowerCase() + ".csv"), type);
        }

        public LocationManager<NamedLocation> createManager(World castWorld) {
            return new FlatFileLocationsManager(
                    new File(rootDir, type.toLowerCase() + File.separator + castWorld.getName() + ".csv"), type);
        }
    }

}
