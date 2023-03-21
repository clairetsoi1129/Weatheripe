package com.techreturners.weatheripe.weather;

import com.techreturners.weatheripe.external.dto.ResponseDTO;
import com.techreturners.weatheripe.weather.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/recipe")
public class RecipeController {
    @Autowired
    private WeatherService weatherService;

    @GetMapping({"/{location}"})
    public ResponseEntity<ResponseDTO> getWeatherByLocation(@PathVariable String location) {
//        weatherService.getWeatherByTemp(10);
        return new ResponseEntity<>(weatherService.getWeatherByLocation(location), HttpStatus.OK);
    }
}
