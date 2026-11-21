package com.homelab.homepage.domain.music.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.homelab.homepage.domain.music.model.MusicPlaylist;

public interface MusicPlaylistRepository extends JpaRepository<MusicPlaylist, Long>
{
    boolean existsByUrl( String url );

    List<MusicPlaylist> findAllByOrderByNameAsc();

    List<MusicPlaylist> findAllByEnabledTrueOrderByNameAsc();
}
