package com.realestate.dialect.parsing.resolvers;

import com.realestate.dialect.model.RealEstateListing;

import com.realestate.dialect.model.Transaction;
import com.realestate.dialect.parsing.language.ParserLanguage;
import com.scraper.flavor.cms.parsing.number.NumbersAsText;
import com.scraper.flavor.cms.parsing.text.NormalizedText;

import java.util.HashSet;
import java.util.List;

/**
 * Resolves missing Transaction fields from description text.
 * Focuses on: deposit, availability, policies (pets, smoking, students), ownership.
 */
public class TransactionResolver extends TextResolver {

    public TransactionResolver(List<ParserLanguage> languages, NumbersAsText[] numberClasses) {
        super(languages, numberClasses);
    }

    @Override
    public String getSectionName() {
        return "Transaction";
    }

    @Override
    public RealEstateListing resolve(RealEstateListing listing, NormalizedText text) {
        if (text == null || text.isBlank()) return listing;

        Transaction transaction = listing.getTransaction();
        if (transaction == null) {
            transaction = new Transaction();
            listing.setTransaction(transaction);
        }

        String textStr = text.value();

        resolveDeposit(transaction, textStr);
        resolveAvailability(transaction, textStr);
        resolvePolicies(transaction, textStr);
        resolveOwnership(listing, transaction, textStr);

        return listing;
    }

    protected void resolveDeposit(Transaction transaction, String text) {
        if (!isMissing(transaction.getSecurityDeposit())) return;

        for (ParserLanguage lang : getLanguages()) {
            Double found = findClosestNumber(text, lang.depositKeywords());
            if (found != null && found > 0) {
                transaction.setSecurityDeposit(Transaction.SecurityDepositEnum.SPECIFIC_AMOUNT);
                transaction.setSecurityDepositValue(found.intValue());
                logResolved("securityDeposit", found.intValue(), lang.name());
                return;
            }
        }
    }

    protected void resolveAvailability(Transaction transaction, String text) {
        if (transaction.getAvailableC() != null) return;

        for (ParserLanguage lang : getLanguages()) {
            if (containsAny(text, lang.availableNowKeywords())) {
                transaction.setAvailableC(Transaction.AvailableChoiceEnum.NOW);
                logResolved("availableC", "NOW", lang.name());
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void resolvePolicies(Transaction transaction, String text) {
        HashSet<Enum> policies = transaction.getPolicies();
        if (policies == null) {
            policies = new HashSet<>();
            transaction.setPolicies(policies);
        }

        for (ParserLanguage lang : getLanguages()) {
            if (!policies.contains(Transaction.PoliciesEnum.PETS_ALLOWED)
                    && containsAny(text, lang.petsAllowedKeywords())) {
                policies.add(Transaction.PoliciesEnum.PETS_ALLOWED);
                logResolved("policy:PETS_ALLOWED", true, lang.name());
            }
            if (!policies.contains(Transaction.PoliciesEnum.SMOKING_ALLOWED)
                    && containsAny(text, lang.smokingAllowedKeywords())) {
                policies.add(Transaction.PoliciesEnum.SMOKING_ALLOWED);
                logResolved("policy:SMOKING_ALLOWED", true, lang.name());
            }
            if (!policies.contains(Transaction.PoliciesEnum.STUDENTS_ALLOWED)
                    && containsAny(text, lang.studentsAllowedKeywords())) {
                policies.add(Transaction.PoliciesEnum.STUDENTS_ALLOWED);
                logResolved("policy:STUDENTS_ALLOWED", true, lang.name());
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void resolveOwnership(RealEstateListing listing, Transaction transaction, String text) {
        if (transaction.getOwnership() != null) return;
        if (listing.getBasic() == null || !listing.getBasic().isBuy()) return;

        for (ParserLanguage lang : getLanguages()) {
            if (containsAny(text, lang.leaseholdKeywords())) {
                transaction.setOwnership(Transaction.OwnershipEnum.LEASEHOLD);
                logResolved("ownership", "LEASEHOLD", lang.name());
                return;
            }
            if (containsAny(text, lang.freeholdKeywords())) {
                transaction.setOwnership(Transaction.OwnershipEnum.FULL_OWNERSHIP);
                logResolved("ownership", "FULL_OWNERSHIP", lang.name());
                return;
            }
        }
    }
}
