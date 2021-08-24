package main.java;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;

/**
 * Class contains all the logic for spending points and getting point balance for all payers.
 */
public class PointsManager {
    private final List<Transaction> transactions = new ArrayList<>();

    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
    }

    /**
     * This method returns a list of points after spending
     * @param points points to spend
     * @return list of points after spending
     */
    public List<PointsAfterSpending> spendPoints(int points) {
        // Count total points and check to make sure it doesn't exceed points to spend
        int totalPoints = transactions.stream().map(t -> t.getPoints()).reduce(0, Integer::sum);

        if (points > totalPoints) {
            throw new IllegalArgumentException("Points to spend has exceeded available points");
        }

        // Sorts the transaction list by timestamp
        Collections.sort(transactions, new Comparator<Transaction>() {
            @Override
            public int compare(Transaction o1, Transaction o2) {
                return o1.getTimestamp().compareTo(o2.getTimestamp());
            }
        });

        List<PointsAfterSpending> pointsAfterSpendingList = new ArrayList<>();

        for (Transaction transaction : transactions) {
            if (points > 0) {
                // Gets the points remaining after spending the points associated with the current transaction
                int pointsAfterSpending = getPayerPoints(transaction.getPayer()) - transaction.getPoints();
                // Checks to see if payer points is not negative
                if (pointsAfterSpending >= 0) {
                    // If the current transaction's points is greater than the points to spend...
                    if (transaction.getPoints() > points) {
                        // add the amount of points spent
                        pointsAfterSpendingList.add(new PointsAfterSpending(transaction.getPayer(), -points));
                        // Subtract points from the current transaction's points
                        transaction.setPoints(transaction.getPoints() - points);
                        break;
                    } else {
                        Optional<PointsAfterSpending> optionalPoints = pointsAfterSpendingList
                                .stream()
                                // Filters by payer
                                .filter(p -> Objects.equals(p.getPayer(), transaction.getPayer()))
                                .findAny();
                        if (optionalPoints.isEmpty()) {
                            // If the payer doesn't exist, add the payer along with the points spent
                            pointsAfterSpendingList.add(new PointsAfterSpending(transaction.getPayer(), -transaction.getPoints()));
                        } else {
                            // Otherwise subtract the existing payer
                            PointsAfterSpending existingPayerPointsAfterSpending = optionalPoints.get();

                            // Subtract the transaction's points from the payer points after spending
                            existingPayerPointsAfterSpending.setPoints(existingPayerPointsAfterSpending.getPoints() - transaction.getPoints());
                        }
                        points -= transaction.getPoints();
                        transaction.setPoints(0);
                    }
                }
            } else {
                break;
            }
        }
        return pointsAfterSpendingList;
    }

    /**
     * This method gets points for the specified payer
     * @param payer the payer to get the points for
     * @return - payer points
     */
    private int getPayerPoints(String payer) {
        AtomicInteger result = new AtomicInteger();

        transactions
                .stream()
                // filter by payer
                .filter(transaction -> transaction.getPayer().equals(payer))
                // for each payer atomically increase the points
                // Note: atomic integer is necessary for thread safety
                .forEach(transaction -> result.addAndGet(transaction.getPoints()));

        return result.get();
    }

    /**
     * This method returns a map of balances by payer when given a list of transactions
     * @return map of balances by payer
     */
    public Map<String, Integer> getPayersPointBalance() {
        return transactions
                .stream()
                // groups by payer
                .collect(groupingBy(
                Transaction::getPayer,
                // creates a new linked hashmap keyed by payer with value which is the sum of all payer points
                LinkedHashMap::new, summingInt(Transaction::getPoints)));
    }
}
