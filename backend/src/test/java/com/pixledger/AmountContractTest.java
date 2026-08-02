package com.pixledger;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AmountContractTest {
  @Test void amountsUseExactDecimalScale() {
    var value = new BigDecimal("0.10").add(new BigDecimal("0.20"));
    assertEquals(new BigDecimal("0.30"), value);
  }
}
