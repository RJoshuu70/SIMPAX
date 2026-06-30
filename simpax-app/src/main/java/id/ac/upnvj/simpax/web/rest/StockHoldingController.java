package id.ac.upnvj.simpax.web.rest;

import id.ac.upnvj.simpax.service.StockHoldingService;
import id.ac.upnvj.simpax.service.dto.StockHoldingDto.BuyRequest;
import id.ac.upnvj.simpax.service.dto.StockHoldingDto.SellRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller StockHolding - portofolio saham pribadi user.
 *
 * <p>Sama seperti WalletBalanceController, tidak ada RBAC check via
 * FortressRbacService di sini - model otorisasinya ownership-based,
 * ditegakkan di StockHoldingService. Lihat Javadoc StockHoldingService
 * untuk penjelasan kenapa buy/sell dibungkus satu @Transactional yang
 * melibatkan WalletBalance sekaligus.
 */
@RestController
@RequestMapping("/api/stocks")
public class StockHoldingController {

    private final StockHoldingService stockService;

    public StockHoldingController(StockHoldingService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/portfolio")
    public ResponseEntity<?> getPortfolio(Principal principal) {
        return ResponseEntity.ok(stockService.getPortfolio(principal.getName()));
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buy(@Valid @RequestBody BuyRequest request, Principal principal) {
        return ResponseEntity.ok(stockService.buy(request, principal.getName()));
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sell(@Valid @RequestBody SellRequest request, Principal principal) {
        return ResponseEntity.ok(stockService.sell(request, principal.getName()));
    }
}
