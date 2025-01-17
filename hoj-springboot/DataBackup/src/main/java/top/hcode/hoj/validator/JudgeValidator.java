package top.hcode.hoj.validator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.hcode.hoj.annotation.HOJAccessEnum;
import top.hcode.hoj.common.exception.StatusFailException;
import top.hcode.hoj.exception.AccessException;
import top.hcode.hoj.pojo.dto.ToJudgeDto;

import java.util.Arrays;
import java.util.List;

/**
 * @Author: Himit_ZH
 * @Date: 2022/3/11 11:20
 * @Description:
 */
@Component
public class JudgeValidator {

    @Autowired
    private AccessValidator accessValidator;

    private final static List<String> HOJ_LANGUAGE_LIST = Arrays.asList("C++", "C++ With O2",
            "C", "C With O2", "Python3", "Python2", "Java", "Golang", "C#", "PHP", "PyPy2", "PyPy3",
            "JavaScript Node", "JavaScript V8");

    public void validateSubmissionInfo(ToJudgeDto toJudgeDto) throws StatusFailException, AccessException {

        if (toJudgeDto.getGid() != null) { // 团队内的提交
            accessValidator.validateAccess(HOJAccessEnum.GROUP_JUDGE);
        } else if (toJudgeDto.getCid() != null && toJudgeDto.getCid() != 0) {
            accessValidator.validateAccess(HOJAccessEnum.CONTEST_JUDGE);
        } else {
            accessValidator.validateAccess(HOJAccessEnum.PUBLIC_JUDGE);
        }

        if (!toJudgeDto.getIsRemote() && !HOJ_LANGUAGE_LIST.contains(toJudgeDto.getLanguage())) {
            throw new StatusFailException("提交的代码的语言错误！请使用" + HOJ_LANGUAGE_LIST + "中之一的语言！");
        }

        if (toJudgeDto.getCode().length() < 50
                && !toJudgeDto.getLanguage().contains("Py")
                && !toJudgeDto.getLanguage().contains("PHP")
                && !toJudgeDto.getLanguage().contains("JavaScript")) {
            throw new StatusFailException("提交的代码是无效的，代码字符长度请不要低于50！");
        }

        if (toJudgeDto.getCode().length() > 65535) {
            throw new StatusFailException("提交的代码是无效的，代码字符长度请不要超过65535！");
        }
    }
}