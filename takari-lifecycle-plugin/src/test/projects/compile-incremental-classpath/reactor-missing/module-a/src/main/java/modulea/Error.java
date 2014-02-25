package modulea;

import moduleb.Missing;

public class Error
{
    // Missing is not found
    public Missing getMissing()
    {
        return new Missing();
    }
}
