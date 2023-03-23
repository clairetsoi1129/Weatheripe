package com.techreturners.weatheripe.weather.service;

import com.techreturners.weatheripe.exception.*;
import com.techreturners.weatheripe.model.RecipeBook;
import com.techreturners.weatheripe.model.UserAccount;
import com.techreturners.weatheripe.recipe.dto.RecipeResponseDTO;
import com.techreturners.weatheripe.recipe.service.RecipeService;
import com.techreturners.weatheripe.repository.UserAccountRepository;
import com.techreturners.weatheripe.user.UserAccountService;
import com.techreturners.weatheripe.weather.dto.RecipeQueryDTO;
import com.techreturners.weatheripe.external.dto.ExternalRequestDto;
import com.techreturners.weatheripe.external.dto.ResponseDTO;
import com.techreturners.weatheripe.external.service.ExternalApiService;
import com.techreturners.weatheripe.model.FoodForWeather;
import com.techreturners.weatheripe.model.Weather;
import com.techreturners.weatheripe.repository.FoodForWeatherRepository;
import com.techreturners.weatheripe.weather.dto.WeatherApiDTO;
import com.techreturners.weatheripe.repository.WeatherRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.text.MessageFormat;
import java.util.*;


@Slf4j
@Service
public class WeatherServiceImpl implements WeatherService {
    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    private WeatherRepository weatherRepository;

    @Autowired
    private FoodForWeatherRepository foodForWeatherRepository;

    @Autowired
    private ExternalApiService externalApiService;

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Value("${weather.api.url}")
    private String WEATHER_API_URL;

    @Value("${weather.api.key}")
    private String WEATHER_API_KEY;

    @Value("${recipe.api.url}")
    private String RECIPE_API_URL;

    @Value("${recipe.app.key}")
    private String RECIPE_APP_KEY;

    @Value("${recipe.app.id}")
    private String RECIPE_APP_ID;


    public ResponseDTO getWeatherByLocation(String location){
        String uri = MessageFormat.format(WEATHER_API_URL,WEATHER_API_KEY, location);
        log.info("*******URI:"+uri);
        ExternalRequestDto externalRequestDto = new ExternalRequestDto(uri,new WeatherApiDTO());
        WeatherApiDTO weatherApiObj;
        try {
            weatherApiObj = (WeatherApiDTO) externalApiService.getResourcesByUri(externalRequestDto);
        }catch (ResourceNotFoundException e){
            throw new WeatherNotFoundException(ExceptionMessages.WEATHER_NOT_FOUND);
        }
        log.info("*******CurrentTemperature:"+weatherApiObj.getCurrentTemp());
        if (weatherApiObj == null || weatherApiObj.getCurrentValues()==null){
            throw new WeatherNotFoundException(ExceptionMessages.WEATHER_NOT_FOUND);
        }
        return weatherApiObj;
    }

    public ResponseDTO buildExternalRecipeAPIQuery(WeatherApiDTO weatherApiObj){
        if (weatherApiObj == null || weatherApiObj.getCurrentValues()==null)
            throw new WeatherNotFoundException(ExceptionMessages.WEATHER_NOT_FOUND);

        String baseUrl = MessageFormat.format(RECIPE_API_URL,RECIPE_APP_KEY, RECIPE_APP_ID);
        StringBuilder stringBuilder = new StringBuilder(baseUrl);
        log.info("*******CurrentTemperature:"+weatherApiObj.getCurrentTemp());
        List<Weather> weathers = weatherRepository
                .findByTemperatureBetweenTemperatureHighLow(weatherApiObj.getCurrentTemp());
        log.info("*******weathers.size():"+weathers.size());
        if (weathers.size() == 0)
            throw new NoMatchingWeatherException(ExceptionMessages.WEATHER_NOT_FOUND);

        List<FoodForWeather> foodForWeathers = foodForWeatherRepository.findByWeatherIdIn(weathers);
        log.info("***foodForWeathers.size():"+foodForWeathers.size());
        if (foodForWeathers.size() == 0)
            throw new NoMatchingFoodException(ExceptionMessages.WEATHER_NOT_FOUND);

        for (FoodForWeather foodForWeather : foodForWeathers) {
            stringBuilder.append("&dishType=");
            stringBuilder.append(foodForWeather.getDishType().getDishTypeLabel());
        }
        log.info("*******final URI:"+ stringBuilder);
        return new RecipeQueryDTO(stringBuilder.toString());
    }

    @Override
    public ResponseDTO getRecipeByLocationForUser(String location, String userToken) throws UserSessionNotFoundException {
        WeatherApiDTO weatherApiDTO = (WeatherApiDTO) getWeatherByLocation(location);
        ResponseDTO recipeQueryDTO = buildExternalRecipeAPIQuery(weatherApiDTO);

        RecipeResponseDTO recipeResponseDTO = (RecipeResponseDTO) recipeService.getRecipeByWeatherCondition(recipeQueryDTO);
        List<RecipeBook> recipeBooks = new ArrayList<>();
        Optional<UserAccount> account = userAccountRepository.findByUserName(userToken);

        if (account.isEmpty()) {
            throw new UserSessionNotFoundException(ExceptionMessages.USER_SESSION_NOT_FOUND);
        }

        new RecipeBook();
        Arrays.stream(recipeResponseDTO.getHits())
                .map(hit ->
                        RecipeBook.builder()
                                .recipeURL(hit.getRecipe().getUrl())
                                .recipeName(hit.getRecipe().getLabel())
                                .calories(Double.parseDouble(hit.getRecipe().getCalories()))
                                .dishType(StringUtils.join(hit.getRecipe().getDishType(), ","))
                                .userId(account.get())
                                .timestamp(java.time.LocalDateTime.now())
                                .build()
                )
                .forEach(recipeBooks::add);

        return userAccountService.saveUserRecipeBook(recipeBooks);
    }


}
