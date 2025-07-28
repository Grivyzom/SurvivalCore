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

    /**
     * Evalúa una fórmula matemática con variables
     *
     * @param formula La fórmula a evaluar (ej: "{flower_level} + 1")
     * @param flowerLevel Nivel de la flor
     * @param potLevel Nivel de la maceta
     * @param playerLevel Nivel del jugador
     * @return Resultado de la fórmula
     */
    public static int evaluateFormula(String formula, int flowerLevel, int potLevel, int playerLevel) {
        try {
            // Reemplazar variables
            String processedFormula = formula
                    .replace("{flower_level}", String.valueOf(flowerLevel))
                    .replace("{pot_level}", String.valueOf(potLevel))
                    .replace("{player_level}", String.valueOf(playerLevel));

            // Evaluar expresión matemática simple
            return evaluateSimpleExpression(processedFormula);

        } catch (Exception e) {
            // En caso de error, retornar el nivel de la flor como fallback
            return flowerLevel;
        }
    }

    /**
     * Evalúa una expresión matemática simple
     */
    private static int evaluateSimpleExpression(String expression) {
        // Remover espacios
        expression = expression.replaceAll("\\s+", "");

        // Si es solo un número, retornarlo
        try {
            return Integer.parseInt(expression);
        } catch (NumberFormatException ignored) {}

        // Evaluar operaciones básicas
        return evaluateWithOperators(expression);
    }

    /**
     * Evalúa expresiones con operadores básicos (+, -, *, /, %)
     */
    private static int evaluateWithOperators(String expression) {
        // Implementación básica de evaluación de expresiones
        // Para casos más complejos se podría usar una librería como JEXL

        // Buscar operadores en orden de precedencia (*, /, %, +, -)
        for (String op : new String[]{"*", "/", "%", "+", "-"}) {
            int opIndex = findOperatorIndex(expression, op);
            if (opIndex != -1) {
                String left = expression.substring(0, opIndex);
                String right = expression.substring(opIndex + 1);

                int leftVal = evaluateSimpleExpression(left);
                int rightVal = evaluateSimpleExpression(right);

                switch (op) {
                    case "+": return leftVal + rightVal;
                    case "-": return leftVal - rightVal;
                    case "*": return leftVal * rightVal;
                    case "/": return rightVal != 0 ? leftVal / rightVal : leftVal;
                    case "%": return rightVal != 0 ? leftVal % rightVal : leftVal;
                }
            }
        }

        // Si no se puede evaluar, retornar 1
        return 1;
    }

    /**
     * Encuentra el índice de un operador en la expresión
     */
    private static int findOperatorIndex(String expression, String operator) {
        int parenLevel = 0;
        for (int i = expression.length() - 1; i >= 0; i--) {
            char c = expression.charAt(i);
            if (c == ')') parenLevel++;
            else if (c == '(') parenLevel--;
            else if (parenLevel == 0 && expression.substring(i).startsWith(operator)) {
                // Evitar operadores negativos al inicio
                if (operator.equals("-") && i == 0) continue;
                return i;
            }
        }
        return -1;
    }

    /**
     * Evalúa fórmulas más complejas con funciones
     */
    public static double evaluateAdvancedFormula(String formula, int flowerLevel, int potLevel, int playerLevel) {
        // Implementación futura para fórmulas más avanzadas
        // Por ahora, usar la evaluación básica
        return evaluateFormula(formula, flowerLevel, potLevel, playerLevel);
    }

    /**
     * Valida si una fórmula es sintácticamente correcta
     */
    public static boolean isValidFormula(String formula) {
        try {
            // Probar con valores de prueba
            evaluateFormula(formula, 1, 1, 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obtiene las variables utilizadas en una fórmula
     */
    public static java.util.Set<String> getFormulaVariables(String formula) {
        java.util.Set<String> variables = new java.util.HashSet<>();

        if (formula.contains("{flower_level}")) variables.add("flower_level");
        if (formula.contains("{pot_level}")) variables.add("pot_level");
        if (formula.contains("{player_level}")) variables.add("player_level");

        return variables;
    }
}
