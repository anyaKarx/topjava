package ru.javawebinar.topjava.util;

import ru.javawebinar.topjava.model.UserMeal;
import ru.javawebinar.topjava.model.UserMealWithExcess;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;
import static ru.javawebinar.topjava.util.TimeUtil.isBetweenHalfOpen;

public class UserMealsUtil {
    public static void main(String[] args) {
        List<UserMeal> meals = Arrays.asList(
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 30, 10, 0), "Завтрак", 500),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 30, 13, 0), "Обед", 1000),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 30, 20, 0), "Ужин", 500),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 31, 0, 0), "Еда на граничное значение", 100),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 31, 10, 0), "Завтрак", 1000),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 31, 13, 0), "Обед", 500),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 31, 20, 0), "Ужин", 410)
        );

        List<UserMealWithExcess> mealsTo = filteredByCycles(meals, LocalTime.of(7, 0), LocalTime.of(12, 0), 2000);
        mealsTo.forEach(System.out::println);

        mealsTo = filteredByStreams(meals, LocalTime.of(7, 0), LocalTime.of(12, 0), 2000);
        mealsTo.forEach(System.out::println);

        mealsTo = optionalFilteredByStreams(meals, LocalTime.of(7, 0), LocalTime.of(12, 0), 2000);
        mealsTo.forEach(System.out::println);
    }


    public static List<UserMealWithExcess> filteredByCycles(List<UserMeal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {

        List<UserMeal> periodMeal = new ArrayList<>();
        Map<LocalDate, Integer> mapCaloriesPerDay = new HashMap<>();

        meals.forEach(meal -> {
            mapCaloriesPerDay.merge(meal.getDay(), meal.getCalories(), Integer::sum);
            if (isBetweenHalfOpen(meal.getTime(), startTime, endTime))
                periodMeal.add(meal);
        });
        List<UserMealWithExcess> result = new ArrayList<>();
        periodMeal.forEach(meal -> {
            boolean excess = mapCaloriesPerDay.get(meal.getDay()) <= caloriesPerDay;
            result.add(new UserMealWithExcess(meal, excess));
        });

        return result;
    }

    public static List<UserMealWithExcess> filteredByStreams(List<UserMeal> meals, LocalTime startTime,
                                                             LocalTime endTime, int caloriesPerDay) {
        Map<LocalDate, Integer> mapCaloriesPerDay =
                meals.stream().collect(groupingBy(UserMeal::getDay, summingInt(UserMeal::getCalories)));

        return meals.stream()
                .filter(t->isBetweenHalfOpen(t.getTime(), startTime, endTime))
                .map(t->new UserMealWithExcess(t,mapCaloriesPerDay.get(t.getDay())>caloriesPerDay))
                .toList();
    }




    public static List<UserMealWithExcess> optionalFilteredByStreams(List<UserMeal> meals, LocalTime startTime,
                                                                     LocalTime endTime, int caloriesPerDay) {


        class PairMealsAndCalories<T> extends ArrayList<T> {
            private int sumCalories;

            public int getSum() {
                return sumCalories;
            }

            public void addCalories(int calories) {
                this.sumCalories += calories;
            }
        }
        class CustomCollector implements Collector<UserMeal, PairMealsAndCalories<UserMeal>, List<UserMealWithExcess>> {
            @Override
            public Supplier<PairMealsAndCalories<UserMeal>> supplier() {
                return PairMealsAndCalories::new;
            }

            @Override
            public BiConsumer<PairMealsAndCalories<UserMeal>, UserMeal> accumulator() {
                return (list, meal) -> {
                    list.addCalories(meal.getCalories());
                    if (isBetweenHalfOpen(meal.getDateTime().toLocalTime(), startTime, endTime)) {
                        list.add(meal);
                    }
                };
            }

            @Override
            public BinaryOperator<PairMealsAndCalories<UserMeal>> combiner() {
                return (list1, list2) -> {
                    list1.addCalories(list2.getSum());
                    list1.addAll(list2);
                    return list1;
                };
            }

            @Override
            public Function<PairMealsAndCalories<UserMeal>, List<UserMealWithExcess>> finisher() {
                return list -> list.stream()
                        .map(meals -> new UserMealWithExcess(meals, list.getSum() > caloriesPerDay))
                        .collect(Collectors.toList());
            }

            @Override
            public Set<Characteristics> characteristics() {
                return new HashSet<>(Arrays.asList(Characteristics.CONCURRENT, Characteristics.UNORDERED));
            }
        }


        return meals.stream().collect(groupingBy(UserMeal::getDay, new CustomCollector()))
                .values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

    }


}

