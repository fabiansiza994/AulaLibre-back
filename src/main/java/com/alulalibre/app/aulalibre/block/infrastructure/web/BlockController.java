package com.alulalibre.app.aulalibre.block.infrastructure.web;

import com.alulalibre.app.aulalibre.block.application.dto.request.CreateBlockRequest;
import com.alulalibre.app.aulalibre.block.application.dto.request.UpdateBlockRequest;
import com.alulalibre.app.aulalibre.block.application.dto.response.BlockResponse;
import com.alulalibre.app.aulalibre.block.application.service.BlockService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/blocks")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @GetMapping
    public List<BlockResponse> findAll() {
        return blockService.findAll();
    }

    @GetMapping("/{id}")
    public BlockResponse findById(@PathVariable Long id) {
        return blockService.findById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<BlockResponse> create(@Valid @RequestBody CreateBlockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(blockService.create(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public BlockResponse update(@PathVariable Long id, @Valid @RequestBody UpdateBlockRequest request) {
        return blockService.update(id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/toggle-active")
    public BlockResponse toggleActive(@PathVariable Long id) {
        return blockService.toggleActive(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        blockService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
