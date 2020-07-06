package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.BitcoinUnit;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.FeeRatesUpdatedEvent;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class SendController extends WalletFormController implements Initializable {
    public static final List<Integer> TARGET_BLOCKS_RANGE = List.of(1, 2, 3, 4, 5, 10, 25, 50);

    public static final double FALLBACK_FEE_RATE = 20000d / 1000;

    @FXML
    private CopyableTextField address;

    @FXML
    private TextField label;

    @FXML
    private TextField amount;

    @FXML
    private ComboBox<BitcoinUnit> amountUnit;

    @FXML
    private Slider targetBlocks;

    @FXML
    private CopyableLabel feeRate;

    @FXML
    private TextField fee;

    @FXML
    private ComboBox<BitcoinUnit> feeAmountUnit;

    @FXML
    private FeeRatesChart feeRatesChart;

    @FXML
    private TransactionDiagram transactionDiagram;

    @FXML
    private Button clear;

    @FXML
    private Button create;

    private final BooleanProperty userFeeSet = new SimpleBooleanProperty(false);

    private final ObjectProperty<UtxoSelector> utxoSelectorProperty = new SimpleObjectProperty<>(null);

    private final ObjectProperty<WalletTransaction> walletTransactionProperty = new SimpleObjectProperty<>(null);

    private final BooleanProperty insufficientInputsProperty = new SimpleBooleanProperty(false);

    private final ChangeListener<String> amountListener = new ChangeListener<String>() {
        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            updateTransaction();
        }
    };

    private final ChangeListener<String> feeListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            userFeeSet.set(true);
            setTargetBlocks(getTargetBlocks());
            updateTransaction();
        }
    };

    private final ChangeListener<Number> targetBlocksListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
            Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
            Integer target = getTargetBlocks();

            if(targetBlocksFeeRates != null) {
                setFeeRate(targetBlocksFeeRates.get(target));
                feeRatesChart.select(target);
            } else {
                feeRate.setText("Unknown");
            }

            Tooltip tooltip = new Tooltip("Target confirmation within " + target + " blocks");
            targetBlocks.setTooltip(tooltip);

            userFeeSet.set(false);
            updateTransaction();
        }
    };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        addValidation();

        address.textProperty().addListener((observable, oldValue, newValue) -> {
            updateTransaction();
        });

        amount.setTextFormatter(new TextFieldValidator(TextFieldValidator.ValidationModus.MAX_FRACTION_DIGITS, 8).getFormatter());
        amount.textProperty().addListener(amountListener);

        amountUnit.getSelectionModel().select(1);
        amountUnit.valueProperty().addListener((observable, oldValue, newValue) -> {
            Long value = getRecipientValueSats(oldValue);
            if(value != null) {
                DecimalFormat df = new DecimalFormat("#.#");
                df.setMaximumFractionDigits(8);
                amount.setText(df.format(newValue.getValue(value)));
            }
        });

        insufficientInputsProperty.addListener((observable, oldValue, newValue) -> {
            amount.textProperty().removeListener(amountListener);
            String amt = amount.getText();
            amount.setText(amt + "0");
            amount.setText(amt);
            amount.textProperty().addListener(amountListener);
            fee.textProperty().removeListener(feeListener);
            String feeAmt = fee.getText();
            fee.setText(feeAmt + "0");
            fee.setText(feeAmt);
            fee.textProperty().addListener(feeListener);
        });

        targetBlocks.setMin(0);
        targetBlocks.setMax(TARGET_BLOCKS_RANGE.size() - 1);
        targetBlocks.setMajorTickUnit(1);
        targetBlocks.setMinorTickCount(0);
        targetBlocks.setLabelFormatter(new StringConverter<Double>() {
            @Override
            public String toString(Double object) {
                String blocks = Integer.toString(TARGET_BLOCKS_RANGE.get(object.intValue()));
                return (object.intValue() == TARGET_BLOCKS_RANGE.size() - 1) ? blocks + "+" : blocks;
            }

            @Override
            public Double fromString(String string) {
                return (double)TARGET_BLOCKS_RANGE.indexOf(Integer.valueOf(string.replace("+", "")));
            }
        });
        targetBlocks.valueProperty().addListener(targetBlocksListener);

        feeRatesChart.initialize();
        Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
        if(targetBlocksFeeRates != null) {
            feeRatesChart.update(targetBlocksFeeRates);
        } else {
            feeRate.setText("Unknown");
        }

        setTargetBlocks(5);

        fee.setTextFormatter(new TextFieldValidator(TextFieldValidator.ValidationModus.MAX_FRACTION_DIGITS, 8).getFormatter());
        fee.textProperty().addListener(feeListener);

        feeAmountUnit.getSelectionModel().select(1);
        feeAmountUnit.valueProperty().addListener((observable, oldValue, newValue) -> {
            Long value = getFeeValueSats(oldValue);
            if(value != null) {
                setFee(value);
            }
        });

        userFeeSet.addListener((observable, oldValue, newValue) -> {
            feeRatesChart.select(0);

            Node thumb = getSliderThumb();
            if(thumb != null) {
                if(newValue) {
                    thumb.getStyleClass().add("inactive");
                } else {
                    thumb.getStyleClass().remove("inactive");
                }
            }
        });

        walletTransactionProperty.addListener((observable, oldValue, walletTransaction) -> {
            if(walletTransaction != null) {
                double feeRate = (double)walletTransaction.getFee() / walletTransaction.getTransaction().getVirtualSize();
                if(userFeeSet.get()) {
                    setTargetBlocks(getTargetBlocks(feeRate));
                } else {
                    setFee(walletTransaction.getFee());
                }

                setFeeRate(feeRate);
            }

            transactionDiagram.update(walletTransaction);
            create.setDisable(walletTransaction == null);
        });

        address.setText("32YSPMaUePf511u5adEckiNq8QLec9ksXX");
    }

    private void addValidation() {
        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.registerValidator(address, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid Address", !newValue.isEmpty() && !isValidRecipientAddress())
        ));
        validationSupport.registerValidator(amount, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Inputs", insufficientInputsProperty.get()),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Value", getRecipientValueSats() != null && getRecipientValueSats() == 0)
        ));
        validationSupport.registerValidator(fee, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Inputs", userFeeSet.get() && insufficientInputsProperty.get()),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Insufficient Fee", getFeeValueSats() != null && getFeeValueSats() == 0)
        ));

        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
    }

    private void updateTransaction() {
        try {
            Address recipientAddress = getRecipientAddress();
            Long recipientAmount = getRecipientValueSats();
            if(recipientAmount != null && recipientAmount != 0 && (!userFeeSet.get() || (getFeeValueSats() != null && getFeeValueSats() > 0))) {
                Wallet wallet = getWalletForm().getWallet();
                WalletTransaction walletTransaction = wallet.createWalletTransaction(getUtxoSelectors(), recipientAddress, recipientAmount, getFeeRate(), userFeeSet.get() ? getFeeValueSats() : null);
                walletTransactionProperty.setValue(walletTransaction);
                insufficientInputsProperty.set(false);

                return;
            }
        } catch (InvalidAddressException e) {
            //ignore
        } catch (InsufficientFundsException e) {
            insufficientInputsProperty.set(true);
        }

        walletTransactionProperty.setValue(null);
    }

    private List<UtxoSelector> getUtxoSelectors() {
        UtxoSelector priorityUtxoSelector = new PriorityUtxoSelector(AppController.getCurrentBlockHeight());
        return List.of(priorityUtxoSelector);
    }

    private boolean isValidRecipientAddress() {
        try {
            getRecipientAddress();
        } catch (InvalidAddressException e) {
            return false;
        }

        return true;
    }

    private Address getRecipientAddress() throws InvalidAddressException {
        return Address.fromString(address.getText());
    }

    private Long getRecipientValueSats() {
        return getRecipientValueSats(amountUnit.getSelectionModel().getSelectedItem());
    }

    private Long getRecipientValueSats(BitcoinUnit bitcoinUnit) {
        if(amount.getText() != null && !amount.getText().isEmpty()) {
            double fieldValue = Double.parseDouble(amount.getText());
            return bitcoinUnit.getSatsValue(fieldValue);
        }

        return null;
    }

    private Long getFeeValueSats() {
        return getFeeValueSats(feeAmountUnit.getSelectionModel().getSelectedItem());
    }

    private Long getFeeValueSats(BitcoinUnit bitcoinUnit) {
        if(fee.getText() != null && !fee.getText().isEmpty()) {
            double fieldValue = Double.parseDouble(fee.getText());
            return bitcoinUnit.getSatsValue(fieldValue);
        }

        return null;
    }

    private Integer getTargetBlocks() {
        int index = (int)targetBlocks.getValue();
        return TARGET_BLOCKS_RANGE.get(index);
    }

    private Integer getTargetBlocks(double feeRate) {
        Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
        int maxTargetBlocks = 1;
        for(Integer targetBlocks : targetBlocksFeeRates.keySet()) {
            maxTargetBlocks = Math.max(maxTargetBlocks, targetBlocks);
            Double candidate = targetBlocksFeeRates.get(targetBlocks);
            if(feeRate > candidate) {
                return targetBlocks;
            }
        }

        return maxTargetBlocks;
    }

    private void setTargetBlocks(Integer target) {
        targetBlocks.valueProperty().removeListener(targetBlocksListener);
        int index = TARGET_BLOCKS_RANGE.indexOf(target);
        targetBlocks.setValue(index);
        feeRatesChart.select(target);
        targetBlocks.valueProperty().addListener(targetBlocksListener);
    }

    private Map<Integer, Double> getTargetBlocksFeeRates() {
        Map<Integer, Double> retrievedFeeRates = AppController.getTargetBlockFeeRates();
        if(retrievedFeeRates == null) {
            retrievedFeeRates = TARGET_BLOCKS_RANGE.stream().collect(Collectors.toMap(java.util.function.Function.identity(), v -> FALLBACK_FEE_RATE));
        }

        return  retrievedFeeRates;
    }

    private Double getFeeRate() {
        return getTargetBlocksFeeRates().get(getTargetBlocks());
    }

    private void setFeeRate(Double feeRateAmt) {
        feeRate.setText(String.format("%.2f", feeRateAmt) + " sats/vByte");
    }

    private void setFee(long feeValue) {
        fee.textProperty().removeListener(feeListener);
        DecimalFormat df = new DecimalFormat("#.#");
        df.setMaximumFractionDigits(8);
        fee.setText(df.format(feeAmountUnit.getValue().getValue(feeValue)));
        fee.textProperty().addListener(feeListener);
    }

    private Node getSliderThumb() {
        return targetBlocks.lookup(".thumb");
    }

    public void setUtxoSelector(UtxoSelector utxoSelector) {
        utxoSelectorProperty.set(utxoSelector);
    }

    public void setMaxInput(ActionEvent event) {

    }

    public void clear(ActionEvent event) {
        address.setText("");
        label.setText("");
        amount.setText("");
        fee.setText("");
        walletTransactionProperty.setValue(null);
    }

    public void createTransaction(ActionEvent event) {

    }

    @Subscribe
    public void feeRatesUpdated(FeeRatesUpdatedEvent event) {
        feeRatesChart.update(event.getTargetBlockFeeRates());
        feeRatesChart.select(getTargetBlocks());
        setFeeRate(event.getTargetBlockFeeRates().get(getTargetBlocks()));
    }
}