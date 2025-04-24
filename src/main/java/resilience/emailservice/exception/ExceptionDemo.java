package resilience.emailservice.exception;

import java.util.Arrays;
import java.util.List;

public class ExceptionDemo {

    public static void main(String[] args) {
        List<String> emails = Arrays.asList("user@example.com", "invalid-email", "admin@example.com");

        // [1] Checked Exception 사용 예제
        // 이메일이 유효하지 않으면 CustomCheckedException을 발생시키고,
        // lambda 내에서 try-catch를 강제하게 됩니다.
        List<String> validEmailsChecked = emails.stream()
                .map(email -> {
                    try {
                        return validateEmailChecked(email);
                    } catch (CustomCheckedException e) {
                        System.out.println("Checked Exception 처리: " + e.getMessage());
                        // 예제에서는 잘못된 이메일은 null로 처리합니다.
                        return null;
                    }
                })
                .filter(email -> email != null)
                .toList();
        System.out.println("유효한 이메일 (Checked): " + validEmailsChecked);

        // [2] Unchecked Exception 사용 예제
        // 이메일이 유효하지 않으면 CustomUncheckedException을 발생시키지만,
        // try-catch를 강제하지 않아 lambda의 가독성이 올라갑니다.
        // (그러나, 예외 발생 시 전체 stream이 중단될 수 있으니 적절한 예외처리가 필요합니다.)
        try {
            List<String> validEmailsUnchecked = emails.stream()
                    .map(email -> validateEmailUnchecked(email))
                    .toList();
            System.out.println("유효한 이메일 (Unchecked): " + validEmailsUnchecked); // 실행 안됨
        } catch (CustomUncheckedException e) {
            System.out.println("Unchecked Exception 처리: " + e.getMessage());
        }
    }

    // Checked Exception을 던지는 메서드
    private static String validateEmailChecked(String email) throws CustomCheckedException {
        if (email.contains("@")) {
            return email;
        } else {
            throw new CustomCheckedException("유효하지 않은 이메일: " + email);
        }
    }

    // Unchecked Exception을 던지는 메서드
    private static String validateEmailUnchecked(String email) {
        if (email.contains("@")) {
            return email;
        } else {
            throw new CustomUncheckedException("유효하지 않은 이메일: " + email);
        }
    }
}