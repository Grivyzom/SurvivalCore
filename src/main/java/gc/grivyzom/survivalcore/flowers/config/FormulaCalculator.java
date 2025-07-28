package gc.grivyzom.survivalcore.flowers.config;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Calculadora para procesar fórmulas matemáticas en las configuraciones de flores
 * Soporta variables como {flower_level}, {pot_level}, etc.
 *
 * @author Brocolitx
 * @version 1.0
 */
public class FormulaCalculator {

    // Patrones para diferentes tipos de variables
    private static final Pattern FLOWER_LEVEL_PATTERN = Pattern.compile("\\{flower_level\\}");
    private static final Pattern POT_LEVEL_PATTERN = Pattern.compile("\\{pot_level\\}");
    private static final Pattern PLAYER_LEVEL_PATTERN = Pattern.compile("\\{player_level\\}");

    /**
     * Calcula una fórmula con el nivel de flor
     */
    public static int calculate(String formula, int flowerLevel) {
        return calculate(formula, flowerLevel, 1, 1);
    }

    /**
     * Calcula una fórmula con nivel de flor y maceta
     */
    public static int calculate(String formula, int flowerLevel, int potLevel) {
        return calculate(formula, flowerLevel, potLevel, 1);
    }

    /**
     * Calcula una fórmula completa con todas las variables
     */
    public static int calculate(String formula, int flowerLevel, int potLevel, int playerLevel) {
        if (formula == null || formula.trim().isEmpty()) {
            return 0;
        }

        try {
            // Reemplazar variables
            String processedFormula = formula;
            processedFormula = FLOWER_LEVEL_PATTERN.matcher(processedFormula)
                    .replaceAll(String.valueOf(flowerLevel));
            processedFormula = POT_LEVEL_PATTERN.matcher(processedFormula)
                    .replaceAll(String.valueOf(potLevel));
            processedFormula = PLAYER_LEVEL_PATTERN.matcher(processedFormula)
                    .replaceAll(String.valueOf(playerLevel));

            // Evaluar la expresión matemática
            return (int) evaluateExpression(processedFormula);

        } catch (Exception e) {
            // Si hay error, devolver el valor por defecto o el nivel de flor
            return Math.max(0, flowerLevel - 1); // 0-indexed para efectos de poción
        }
    }

    /**
     * Evalúa una expresión matemática simple
     * Soporta: +, -, *, /, (), números enteros y decimales
     */
    private static double evaluateExpression(String expression) {
        // Remover espacios
        expression = expression.replaceAll("\\s+", "");

        // Si es solo un número, devolverlo directamente
        try {
            return Double.parseDouble(expression);
        } catch (NumberFormatException e) {
            // Continuar con evaluación compleja
        }

        // Evaluar expresiones con paréntesis primero
        while (expression.contains("(")) {
            int lastOpen = expression.lastIndexOf('(');
            int firstClose = expression.indexOf(')', lastOpen);

            if (firstClose == -1) {
                throw new IllegalArgumentException("Paréntesis no balanceados");
            }

            String subExpression = expression.substring(lastOpen + 1, firstClose);
            double subResult = evaluateSimpleExpression(subExpression);

            expression = expression.substring(0, lastOpen) +
                    subResult +
                    expression.substring(firstClose + 1);
        }

        return evaluateSimpleExpression(expression);
    }

    /**
     * Evalúa una expresión simple sin paréntesis
     */
    private static double evaluateSimpleExpression(String expression) {
        // Manejar números negativos al inicio
        if (expression.startsWith("-")) {
            expression = "0" + expression;
        }

        // Buscar operaciones en orden de precedencia: *, /, +, -
        return evaluateAdditionSubtraction(expression);
    }

    /**
     * Evalúa suma y resta (menor precedencia)
     */
    private static double evaluateAdditionSubtraction(String expression) {
        // Buscar + o - de derecha a izquierda (fuera de números negativos)
        for (int i = expression.length() - 1; i >= 1; i--) {
            char c = expression.charAt(i);
            if (c == '+' || c == '-') {
                // Verificar que no sea parte de un número negativo
                if (c == '-' && Character.isDigit(expression.charAt(i-1))) {
                    String left = expression.substring(0, i);
                    String right = expression.substring(i + 1);

                    if (c == '+') {
                        return evaluateMultiplicationDivision(left) + evaluateMultiplicationDivision(right);
                    } else {
                        return evaluateMultiplicationDivision(left) - evaluateMultiplicationDivision(right);
                    }
                }
            }
        }

        return evaluateMultiplicationDivision(expression);
    }

    /**
     * Evalúa multiplicación y división (mayor precedencia)
     */
    private static double evaluateMultiplicationDivision(String expression) {
        // Buscar * o / de derecha a izquierda
        for (int i = expression.length() - 1; i >= 1; i--) {
            char c = expression.charAt(i);
            if (c == '*' || c == '/') {
                String left = expression.substring(0, i);
                String right = expression.substring(i + 1);

                if (c == '*') {
                    return evaluateMultiplicationDivision(left) * parseNumber(right);
                } else {
                    double rightValue = parseNumber(right);
                    if (rightValue == 0) {
                        throw new ArithmeticException("División por cero");
                    }
                    return evaluateMultiplicationDivision(left) / rightValue;
                }
            }
        }

        return parseNumber(expression);
    }

    /**
     * Convierte una string a número
     */
    private static double parseNumber(String numberStr) {
        try {
            return Double.parseDouble(numberStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Número inválido: " + numberStr);
        }
    }

    /**
     * Valida que una fórmula sea válida sintácticamente
     */
    public static boolean isValidFormula(String formula) {
        if (formula == null || formula.trim().isEmpty()) {
            return false;
        }

        try {
            // Probar la fórmula con valores de prueba
            calculate(formula, 1, 1, 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obtiene las variables utilizadas en una fórmula
     */
    public static java.util.Set<String> getUsedVariables(String formula) {
        java.util.Set<String> variables = new java.util.HashSet<>();

        if (formula == null) return variables;

        if (FLOWER_LEVEL_PATTERN.matcher(formula).find()) {
            variables.add("flower_level");
        }
        if (POT_LEVEL_PATTERN.matcher(formula).find()) {
            variables.add("pot_level");
        }
        if (PLAYER_LEVEL_PATTERN.matcher(formula).find()) {
            variables.add("player_level");
        }

        return variables;
    }

    /**
     * Reemplaza variables en una string (útil para lore y nombres)
     */
    public static String replaceVariables(String text, int flowerLevel, int potLevel, int playerLevel) {
        if (text == null) return null;

        String result = text;
        result = FLOWER_LEVEL_PATTERN.matcher(result).replaceAll(String.valueOf(flowerLevel));
        result = POT_LEVEL_PATTERN.matcher(result).replaceAll(String.valueOf(potLevel));
        result = PLAYER_LEVEL_PATTERN.matcher(result).replaceAll(String.valueOf(playerLevel));

        return result;
    }

    /**
     * Calcula el valor mínimo posible de una fórmula
     */
    public static int calculateMinValue(String formula, int maxFlowerLevel, int maxPotLevel) {
        int minValue = Integer.MAX_VALUE;

        for (int flowerLevel = 1; flowerLevel <= maxFlowerLevel; flowerLevel++) {
            for (int potLevel = 1; potLevel <= maxPotLevel; potLevel++) {
                try {
                    int value = calculate(formula, flowerLevel, potLevel, 1);
                    minValue = Math.min(minValue, value);
                } catch (Exception e) {
                    // Ignorar errores y continuar
                }
            }
        }

        return minValue == Integer.MAX_VALUE ? 0 : minValue;
    }

    /**
     * Calcula el valor máximo posible de una fórmula
     */
    public static int calculateMaxValue(String formula, int maxFlowerLevel, int maxPotLevel) {
        int maxValue = Integer.MIN_VALUE;

        for (int flowerLevel = 1; flowerLevel <= maxFlowerLevel; flowerLevel++) {
            for (int potLevel = 1; potLevel <= maxPotLevel; potLevel++) {
                try {
                    int value = calculate(formula, flowerLevel, potLevel, 100); // Nivel de jugador alto
                    maxValue = Math.max(maxValue, value);
                } catch (Exception e) {
                    // Ignorar errores y continuar
                }
            }
        }

        return maxValue == Integer.MIN_VALUE ? 0 : maxValue;
    }
}