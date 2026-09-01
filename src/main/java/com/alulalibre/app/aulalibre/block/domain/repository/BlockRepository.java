package com.alulalibre.app.aulalibre.block.domain.repository;

import com.alulalibre.app.aulalibre.block.domain.model.Block;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockRepository extends JpaRepository<Block, Long> {

    Optional<Block> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
