package ch.epfl.vlsc.settings;

import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SizeValueParser {

    public SizeValueParser() {}

    public Long parse(String text) throws CompilationException {
        Optional<Long> floatSize = readFloat(text);
        Optional<Long> integerSize = readInteger(text);
        Optional<Long> byteSize = readByteInteger(text);
        Optional<Long> unitless = readUnitless(text);

        if (floatSize.isPresent()) {
            return floatSize.get();
        } else if (integerSize.isPresent()) {
            return integerSize.get();
        } else if (byteSize.isPresent()) {
            return byteSize.get();
        } else if (unitless.isPresent()) {
            return  unitless.get();
        } else {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "could not parse " + text));

        }
    }


    private Optional<Long> readFloat(String text) {
        String floatRegEx = "(\\s*)(\\d+)\\.(\\d+)(Mi|Ki|Gi|M|K|G)B(\\s*)";
        Pattern floatPattern = Pattern.compile(floatRegEx);
        Matcher matchter = floatPattern.matcher(text);
        if (matchter.matches()) {

            Double sizeValue = Double.valueOf(matchter.group(2) + "." + matchter.group(3));
            sizeValue *= Double.valueOf(getUnitMultiplier(matchter.group(4)));
            return Optional.of(Long.valueOf(sizeValue.longValue()));
        }
        return  Optional.empty();
    }

    private Optional<Long> readInteger(String text) {
        String integerRegEx = "(\\s*)(\\d+)(\\s*)(Mi|Ki|Gi|M|K|G)B(\\s*)";
        Pattern integerPattern = Pattern.compile(integerRegEx);
        Matcher matcher = integerPattern.matcher(text);
        if (matcher.matches()) {

            Long sizeValue = Long.valueOf(matcher.group(2));
            sizeValue *= getUnitMultiplier(matcher.group(4));
            return Optional.of(sizeValue);
        }

        return Optional.empty();

    }

    public Optional<Long> readByteInteger(String text) {
        String byteIntegerRegEx = "(\\s*)(\\d+)(\\s*)B(\\s*)";
        Pattern bytePattern = Pattern.compile(byteIntegerRegEx);
        Matcher matcher = bytePattern.matcher(text);
        if (matcher.matches()) {
            Long sizevalue = Long.valueOf(matcher.group(2));
            return Optional.of(sizevalue);
        }
        return Optional.empty();
    }

    private Optional<Long> readUnitless(String text) {
        return readByteInteger(text + "B");
    }
    private Long getUnitMultiplier(String unit) {

        switch (unit) {
            case "Ki":
            case "K":
                return Long.valueOf(1024);
            case "Mi":
            case "M":
                return Long.valueOf(1024 * 1024);
            case "Gi":
            case "G":
                return Long.valueOf(1024 * 1024 * 1024);
            default:
                throw new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR,
                                "can not parse unit" + unit));
        }

    }
}
