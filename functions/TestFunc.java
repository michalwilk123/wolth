package functions;
import java.time.LocalDateTime;  
import java.time.Period; 

public class TestFunc {
    public static int timeSince(int d, int m, int y){
        LocalDateTime datetime1 = LocalDateTime.now();  
        LocalDateTime a = LocalDateTime.of(y, m, d, 0, 0);    
        Period period = Period.between(a.toLocalDate(), datetime1.toLocalDate());

        return (int) (
            period.getYears() * 365.25 + 
            period.getMonths() * 30.4375 + 
            period.getDays() + 1000
        );
    }
}