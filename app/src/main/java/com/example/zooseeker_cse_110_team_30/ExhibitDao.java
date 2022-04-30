package com.example.zooseeker_cse_110_team_30;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ExhibitDao {

    @Query("SELECT * FROM `exhibit` WHERE `id`=:id")
    Exhibit get(long id);

    @Query("SELECT * FROM `exhibit` WHERE `kind`='exhibit' AND `name`=:name LIMIT 1")
    Exhibit getName(String name);

    @Query("SELECT * FROM `exhibit` WHERE :tag IN (`tags`) ORDER BY `name` ASC")
    Exhibit getTag(String tag);

    @Query("SELECT * FROM `exhibit` WHERE `kind`='exhibit'")
    List<Exhibit> getAll();

    @Insert
    List<Long> insertAll(List<Exhibit> exhibit);
}
