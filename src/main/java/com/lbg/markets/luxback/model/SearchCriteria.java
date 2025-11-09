package com.lbg.markets.luxback.model;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SearchCriteria {

    String filename;
    String username;
    LocalDate startDate;
    LocalDate endDate;
}
